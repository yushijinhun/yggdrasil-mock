package org.to2mbn.yggdrasil.mockserver;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.CacheControl.maxAge;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.http.MediaType.IMAGE_PNG;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.noContent;
import static org.springframework.web.reactive.function.server.ServerResponse.notFound;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.randomUnsignedUUID;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.toUUID;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_invalid_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_invalid_token;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_no_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_profile_not_found;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_token_already_assigned;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newForbiddenOperationException;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newIllegalArgumentException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
					YggdrasilUser user = passwordAuthenticated(req.username,req.password);

					if (req.clientToken == null)
						req.clientToken = randomUnsignedUUID();

					Token token = tokenStore.acquireToken(user, req.clientToken, null);

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
					Token oldToken = authenticated(req.accessToken, req.clientToken, AvailableLevel.PARTIAL);

					YggdrasilCharacter characterToSelect = null;
					if (req.selectedProfile != null) {
						if (oldToken.getBoundCharacter().isPresent())
							throw newIllegalArgumentException(m_token_already_assigned);

						characterToSelect = database.findCharacterByUUID(toUUID(req.selectedProfile.id))
							.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));

						if (!characterToSelect.getName().equals(req.selectedProfile.name))
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
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })))

		.andRoute(POST("/authserver/validate"),
			request -> request.bodyToMono(ValidateRequest.class)
				.flatMap(req -> {
					authenticated(req.accessToken, req.clientToken, AvailableLevel.COMPLETE);
					return noContent().build();
				})
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })))

		.andRoute(POST("/authserver/invalidate"),
			request -> request.bodyToMono(InvalidateRequest.class)
				.flatMap(req -> {
					if (req.accessToken == null)
						throw newIllegalArgumentException(m_no_credentials);

					tokenStore.findToken(req.accessToken).ifPresent(Token::revoke);

					return noContent().build();
				})
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })))

		.andRoute(POST("/authserver/signout"),
			request -> request.bodyToMono(SignoutRequest.class)
				.flatMap(req -> {
					YggdrasilUser user = passwordAuthenticated(req.username, req.password);
					tokenStore.revokeAll(user);
					return noContent().build();
				})
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException(m_no_credentials); })))

		.andRoute(POST("/api/profiles/minecraft"),
			request -> request.bodyToMono(String[].class)
				.map(Stream::of)
				.map(names -> names
					.distinct()
					.map(database::findCharacterByName)
					.flatMap(Optional::stream)
					.map(YggdrasilCharacter::toSimpleResponse)
					.collect(toList()))
				.flatMap(result -> ok()
					.contentType(APPLICATION_JSON_UTF8)
					.syncBody(result))
				.switchIfEmpty(Mono.defer(() -> { throw newIllegalArgumentException("empty request"); })))

		.andRoute(GET("/textures/{hash:[a-f0-9]{64}}"),
			request -> database.findTextureByHash(request.pathVariable("hash"))
				.map(texture -> ok()
					.contentType(IMAGE_PNG)
					.contentLength(texture.getData().length)
					.eTag(texture.getHash())
					.cacheControl(maxAge(30, DAYS).cachePublic())
					.syncBody(texture.getData()))
				.orElseGet(() -> notFound().build()))
		// @formatter:on
		;
	}

	private void rateLimit(YggdrasilUser user) {
		if (!rateLimiter.tryAccess(user)) {
			throw newForbiddenOperationException(m_invalid_credentials);
		}
	}

	private static enum AvailableLevel {
		COMPLETE, PARTIAL;
	}

	private YggdrasilUser passwordAuthenticated(@Nullable String username, @Nullable String password) {
		if (username == null || password == null)
			throw newIllegalArgumentException(m_no_credentials);

		YggdrasilUser user = database.findUserByEmail(username).orElseThrow(() -> newForbiddenOperationException(m_invalid_credentials));
		rateLimit(user);
		if (!password.equals(user.getPassword()))
			throw newForbiddenOperationException(m_invalid_credentials);
		return user;
	}

	private Token authenticated(@Nullable String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		if (accessToken == null)
			throw newIllegalArgumentException(m_no_credentials);

		Token token = tokenStore.findToken(accessToken)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));

		if (clientToken != null && !clientToken.equals(token.getClientToken()))
			throw newForbiddenOperationException(m_invalid_token);

		switch (availableLevel) {
			case COMPLETE:
				if (token.isValid()) {
					return token;
				}
				break;
			case PARTIAL:
				if (token.isRefreshable()) {
					return token;
				}
				break;
		}
		throw newForbiddenOperationException(m_invalid_token);
	}

	// ---- Requests ----
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class LoginRequest {
		public String username;
		public String password;
		public String clientToken;
		public boolean requestUser = false;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RefreshRequest {
		public String accessToken;
		public String clientToken;
		public boolean requestUser = false;
		public ProfileBody selectedProfile;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ProfileBody {
		public String id;
		public String name;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ValidateRequest {
		public String accessToken;
		public String clientToken;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class InvalidateRequest {
		public String accessToken;
		public String clientToken;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SignoutRequest {
		public String username;
		public String password;
	}
	// --------

}
