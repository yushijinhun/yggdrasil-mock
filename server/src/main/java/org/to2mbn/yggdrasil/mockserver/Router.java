package org.to2mbn.yggdrasil.mockserver;

import static java.util.stream.Collectors.toList;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.randomUnsignedUUID;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.toUUID;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_invalid_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_no_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_profile_not_found;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_token_already_assigned;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newForbiddenOperationException;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newIllegalArgumentException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.to2mbn.yggdrasil.mockserver.TokenStore.Token;
import org.to2mbn.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilCharacter;
import org.to2mbn.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import reactor.core.publisher.Mono;

@Configuration
public class Router {

	@Autowired
	private ServerMeta meta;

	@Autowired
	private RateLimiter rateLimiter;

	@Autowired
	private YggdrasilDatabase database;

	@Autowired
	private TokenStore tokenStore;

	@Bean
	public RouterFunction<ServerResponse> apiRouter() {
		return
		// @formatter:off
		route(GET("/"),
			req -> ok()
				.contentType(APPLICATION_JSON_UTF8)
				.syncBody(meta))

		.andRoute(POST("/authserver/authenticate"),
			request -> request.bodyToMono(LoginRequest.class)
				.flatMap(req -> {
					if (req.username == null || req.password == null)
						throw newIllegalArgumentException(m_no_credentials);

					YggdrasilUser user = database.findUserByEmail(req.username).orElseThrow(() -> newForbiddenOperationException(m_invalid_credentials));
					rateLimit(user);
					if (!req.password.equals(user.getPassword()))
						throw newForbiddenOperationException(m_invalid_credentials);

					if (req.clientToken == null)
						req.clientToken = randomUnsignedUUID();

					Token token = tokenStore.acquireToken(user, req.clientToken,null);

					Map<String, Object> response = new LinkedHashMap<>();
					response.put("accessToken", token.getAccessToken());
					response.put("clientToken", token.getClientToken());
					response.put("availableProfiles",
					user.getCharacters().stream()
						.map(YggdrasilCharacter::toSimpleResponse)
						.collect(toList()));
					token.getBoundCharacter().ifPresent(
						it -> response.put("selectedProfile", it.toSimpleResponse()));

					if (req.requestUser)
						response.put("user", user.toResponse());

					return ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(response);
				})
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })))

		.andRoute(POST("/authserver/refresh"),
			request -> request.bodyToMono(RefreshRequest.class)
				.flatMap(req -> {
					Token oldToken = authenticated(req.accessToken, req.clientToken);

					YggdrasilCharacter characterToSelect = null;
					if (req.selectedProfile != null) {
						if (oldToken.getBoundCharacter().isPresent())
							throw newIllegalArgumentException(m_token_already_assigned);

						characterToSelect = database.findCharacterByUUID(toUUID(req.getSelectedProfile().getId()))
							.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));

						if (!characterToSelect.getName().equals(req.getSelectedProfile().getName()))
							throw newIllegalArgumentException(m_profile_not_found);
					}

					Token newToken = tokenStore.acquireToken(oldToken.getUser(), oldToken.getClientToken(), characterToSelect);
					oldToken.revoke();

					Map<String, Object> response = new LinkedHashMap<>();
					response.put("accessToken", newToken.getAccessToken());
					response.put("clientToken", newToken.getClientToken());
					newToken.getBoundCharacter().ifPresent(
						it -> response.put("selectedProfile", it.toSimpleResponse()));

					if (req.requestUser)
						response.put("user", newToken.getUser().toResponse());

					return ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(response);
				})
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })));
		// @formatter:on
	}

	private void rateLimit(YggdrasilUser user) {
		if (!rateLimiter.tryAccess(user)) {
			throw newForbiddenOperationException(m_invalid_credentials);
		}
	}

	private Token authenticated(@Nullable String accessToken, @Nullable String clientToken) {
		if (accessToken == null)
			throw newIllegalArgumentException(m_no_credentials);

		Token token = tokenStore.findToken(accessToken)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_credentials));

		if (clientToken != null && !clientToken.equals(token.getClientToken()))
			throw newForbiddenOperationException(m_invalid_credentials);

		return token;
	}

	// ---- Requests ----
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class LoginRequest {
		private String username;
		private String password;
		private String clientToken;
		private boolean requestUser = false;

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getClientToken() {
			return clientToken;
		}

		public void setClientToken(String clientToken) {
			this.clientToken = clientToken;
		}

		public boolean isRequestUser() {
			return requestUser;
		}

		public void setRequestUser(boolean requestUser) {
			this.requestUser = requestUser;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RefreshRequest {
		private String accessToken;
		private String clientToken;
		private boolean requestUser = false;
		private ProfileBody selectedProfile;

		public String getAccessToken() {
			return accessToken;
		}

		public void setAccessToken(String accessToken) {
			this.accessToken = accessToken;
		}

		public String getClientToken() {
			return clientToken;
		}

		public void setClientToken(String clientToken) {
			this.clientToken = clientToken;
		}

		public boolean isRequestUser() {
			return requestUser;
		}

		public void setRequestUser(boolean requestUser) {
			this.requestUser = requestUser;
		}

		public ProfileBody getSelectedProfile() {
			return selectedProfile;
		}

		public void setSelectedProfile(ProfileBody selectedProfile) {
			this.selectedProfile = selectedProfile;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ProfileBody {
		private String id;
		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}
	// --------

}
