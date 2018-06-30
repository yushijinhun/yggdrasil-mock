package org.to2mbn.yggdrasil.mockserver;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
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
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.unsign;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_access_denied;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_invalid_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_invalid_token;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_no_credentials;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_profile_not_found;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_token_already_assigned;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newForbiddenOperationException;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newIllegalArgumentException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.to2mbn.yggdrasil.mockserver.TokenStore.AvailableLevel;
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

	@Autowired
	private SessionAuthenticator sessionAuth;

	private static final Mono<ServerResponse> ON_EMPTY_REQUEST = Mono.defer(() -> {
		throw newIllegalArgumentException("empty request");
	});

	@Bean
	public RouterFunction<ServerResponse> rootRouter() {
		return route(GET("/"),
				req -> ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(meta));
	}

	@Bean
	public RouterFunction<ServerResponse> statusRouter() {
		// @formatter:off
		return route(GET("/status"),
				req -> ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(
							ofEntries(
								entry("user.count", database.getUsers().size()),
								entry("token.count", tokenStore.tokensCount()),
								entry("pendingAuthentication.count", sessionAuth.pendingAuthenticationsCount())
							)
						));
		// @formatter:on
	}

	@Bean
	public RouterFunction<ServerResponse> authRouter() {
		return
		// @formatter:off
			route(POST("/authserver/authenticate"),
					request -> request.bodyToMono(LoginRequest.class)
							.flatMap(this::processLogin)
							.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(POST("/authserver/refresh"),
					request -> request.bodyToMono(RefreshRequest.class)
							.flatMap(this::processRefresh)
							.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(POST("/authserver/validate"),
					request -> request.bodyToMono(ValidateRequest.class)
						.flatMap(this::processValidate)
						.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(POST("/authserver/invalidate"),
					request -> request.bodyToMono(InvalidateRequest.class)
							.flatMap(this::processInvalidate)
							.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(POST("/authserver/signout"),
					request -> request.bodyToMono(SignoutRequest.class)
							.flatMap(this::processSignout)
							.switchIfEmpty(ON_EMPTY_REQUEST));
		// @formatter:on
	}

	@Bean
	public RouterFunction<ServerResponse> profileRouter() {
		return
		// @formatter:off
			route(POST("/api/profiles/minecraft"),
					request -> request.bodyToMono(String[].class)
							.flatMap(this::processQueryProfiles)
							.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(GET("/sessionserver/session/minecraft/profile/{uuid:[a-f0-9]{32}}"),
					request -> processGetProfile(
							toUUID(request.pathVariable("uuid")),
							request.queryParam("unsigned").orElse("true")
									.equals("false")));
		// @formatter:on
	}

	@Bean
	public RouterFunction<ServerResponse> sessionRouter() {
		return
		// @formatter:off
			route(POST("/sessionserver/session/minecraft/join"),
					request -> request.bodyToMono(JoinServerRequest.class)
							.flatMap(req -> processJoinServer(req, getRemoteAddr(request)))
							.switchIfEmpty(ON_EMPTY_REQUEST))

			.andRoute(GET("/sessionserver/session/minecraft/hasJoined"),
					request -> processHasJoinedServer(
							request.queryParam("serverId"),
							request.queryParam("username"),
							request.queryParam("ip")));
		// @formatter:on
	}

	@Bean
	public RouterFunction<ServerResponse> textureRouter() {
		return route(GET("/textures/{hash:[a-f0-9]{64}}"),
				request -> processGetTexture(request.pathVariable("hash")));
	}

	private Mono<ServerResponse> processLogin(LoginRequest req) {
		YggdrasilUser user = passwordAuthenticated(req.username, req.password);

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
	}

	private Mono<ServerResponse> processRefresh(RefreshRequest req) {
		YggdrasilCharacter characterToSelect =
				req.selectedProfile == null ? null
						: database.findCharacterByUUID(toUUID(req.selectedProfile.id))
								.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));

		if (characterToSelect != null && !characterToSelect.getName().equals(req.selectedProfile.name))
			throw newIllegalArgumentException(m_profile_not_found);

		Token oldToken = authenticateAndConsume(req.accessToken, req.clientToken, AvailableLevel.PARTIAL,
				token -> {
					if (characterToSelect != null) {
						if (token.getBoundCharacter().isPresent())
							throw newIllegalArgumentException(m_token_already_assigned);

						if (characterToSelect.getOwner() != token.getUser())
							throw newForbiddenOperationException(m_access_denied);
					}
					return true;
				});

		Token newToken = tokenStore.acquireToken(oldToken.getUser(), oldToken.getClientToken(),
				characterToSelect == null ? oldToken.getBoundCharacter().orElse(null) : characterToSelect);

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
	}

	private Mono<ServerResponse> processValidate(ValidateRequest req) {
		authenticate(req.accessToken, req.clientToken, AvailableLevel.COMPLETE);
		return noContent().build();
	}

	private Mono<ServerResponse> processInvalidate(InvalidateRequest req) {
		if (req.accessToken == null)
			throw newIllegalArgumentException(m_no_credentials);

		tokenStore.authenticateAndConsume(req.accessToken, null, AvailableLevel.PARTIAL, dummy -> true);
		return noContent().build();
	}

	private Mono<ServerResponse> processSignout(SignoutRequest req) {
		YggdrasilUser user = passwordAuthenticated(req.username, req.password);
		tokenStore.revokeAll(user);
		return noContent().build();
	}

	private Mono<ServerResponse> processJoinServer(JoinServerRequest req, Optional<String> ipAddr) {
		if (req.serverId == null)
			throw newIllegalArgumentException("serverId is null");
		if (req.selectedProfile == null)
			throw newIllegalArgumentException("selectedProfile is null");

		Token token = authenticate(req.accessToken, null, AvailableLevel.COMPLETE);
		if (token.getBoundCharacter().isPresent()
				&& unsign(token.getBoundCharacter().get().getUuid()).equals(req.selectedProfile)) {
			sessionAuth.joinServer(token, req.serverId, ipAddr.orElse(null));
			return noContent().build();
		} else {
			throw newForbiddenOperationException("invalid profile");
		}
	}

	private Mono<ServerResponse> processHasJoinedServer(Optional<String> serverId, Optional<String> username, Optional<String> ip) {
		if (serverId.isPresent() && username.isPresent()) {
			Optional<YggdrasilCharacter> character = sessionAuth.verifyUser(username.get(), serverId.get(), ip.orElse(null));
			if (character.isPresent()) {
				return ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(character.get().toCompleteResponse(true));
			}
		}
		return noContent().build();
	}

	private Mono<ServerResponse> processQueryProfiles(String[] req) {
		return ok()
				.contentType(APPLICATION_JSON_UTF8)
				.syncBody(
						Stream.of(req)
								.distinct()
								.map(database::findCharacterByName)
								.flatMap(Optional::stream)
								.map(YggdrasilCharacter::toSimpleResponse)
								.collect(toList()));
	}

	private Mono<ServerResponse> processGetProfile(UUID uuid, boolean signed) {
		return database.findCharacterByUUID(uuid)
				.map(character -> character.toCompleteResponse(signed))
				.map(response -> ok()
						.contentType(APPLICATION_JSON_UTF8)
						.syncBody(response))
				.orElseGet(() -> noContent().build());
	}

	private Mono<ServerResponse> processGetTexture(String hash) {
		return database.findTextureByHash(hash)
				.map(texture -> ok()
						.contentType(IMAGE_PNG)
						.contentLength(texture.getData().length)
						.eTag(texture.getHash())
						.cacheControl(maxAge(30, DAYS).cachePublic())
						.syncBody(texture.getData()))
				.orElseGet(() -> notFound().build());
	}

	// ---- Helper methods ----
	private Optional<String> getRemoteAddr(ServerRequest request) {
		return request.attribute(IpAttributeFilter.ATTR_REMOTE_ADDR)
				.map(InetSocketAddress.class::cast)
				.map(InetSocketAddress::getAddress)
				.map(InetAddress::getHostAddress);
	}

	private void rateLimit(YggdrasilUser user) {
		if (!rateLimiter.tryAccess(user)) {
			throw newForbiddenOperationException(m_invalid_credentials);
		}
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

	private Token authenticate(@Nullable String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		if (accessToken == null)
			throw newIllegalArgumentException(m_no_credentials);

		return tokenStore.authenticate(accessToken, clientToken, availableLevel)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	private Token authenticateAndConsume(@Nullable String accessToken, @Nullable String clientToken, AvailableLevel availableLevel, Predicate<Token> checker) {
		if (accessToken == null)
			throw newIllegalArgumentException(m_no_credentials);

		return tokenStore.authenticateAndConsume(accessToken, clientToken, availableLevel, checker)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	// --------

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

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class JoinServerRequest {
		public String accessToken;
		public String selectedProfile;
		public String serverId;
	}
	// --------

}
