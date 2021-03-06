package moe.yushi.yggdrasil_mock;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.stream.Collectors.toList;
import static moe.yushi.yggdrasil_mock.UUIDUtils.randomUnsignedUUID;
import static moe.yushi.yggdrasil_mock.UUIDUtils.toUUID;
import static moe.yushi.yggdrasil_mock.UUIDUtils.unsign;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_access_denied;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_invalid_credentials;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_invalid_token;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_profile_not_found;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.m_token_already_assigned;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.newForbiddenOperationException;
import static moe.yushi.yggdrasil_mock.exception.YggdrasilException.newIllegalArgumentException;
import static org.springframework.http.CacheControl.maxAge;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.MediaType.IMAGE_PNG;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.ok;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import moe.yushi.yggdrasil_mock.TokenStore.AvailableLevel;
import moe.yushi.yggdrasil_mock.TokenStore.Token;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.ModelType;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.TextureType;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.YggdrasilUser;

@Validated
@RestController
public class Router {

	private final Logger logger = LoggerFactory.getLogger(Router.class);

	private @Autowired ServerMeta meta;
	private @Autowired RateLimiter rateLimiter;
	private @Autowired YggdrasilDatabase database;
	private @Autowired TokenStore tokenStore;
	private @Autowired SessionAuthenticator sessionAuth;
	private @Autowired Texture.Storage texturesStorage;
	private @Value("${yggdrasil.core.login-with-character-name}") boolean loginWithCharacterName;

	@GetMapping("/")
	public ServerMeta root() {
		return meta;
	}

	@GetMapping("/status")
	public Map<?, ?> status() {
		return ofEntries(
				entry("user.count", database.getUsers().size()),
				entry("token.count", tokenStore.tokensCount()),
				entry("pendingAuthentication.count", sessionAuth.pendingAuthenticationsCount()));
	}

	@PostMapping("/authserver/authenticate")
	public Map<?, ?> authenticate(@RequestBody @Valid LoginRequest req) {
		YggdrasilUser user;
		YggdrasilCharacter character = null;
		if (loginWithCharacterName) {
			character = database.findCharacterByName(req.username).orElse(null);
		}
		if (character == null) {
			user = passwordAuthenticated(req.username, req.password);
		} else {
			user = passwordAuthenticated(character.getOwner().getEmail(), req.password);
		}

		if (req.clientToken == null)
			req.clientToken = randomUnsignedUUID();

		var token = tokenStore.acquireToken(user, req.clientToken, character);

		var response = new LinkedHashMap<>();
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

		return response;
	}

	@PostMapping("/authserver/refresh")
	public Map<?, ?> refresh(@RequestBody @Valid RefreshRequest req) {
		var characterToSelect = req.selectedProfile == null ? null
				: database.findCharacterByUUID(toUUID(req.selectedProfile.id))
						.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));

		if (characterToSelect != null && !characterToSelect.getName().equals(req.selectedProfile.name))
			throw newIllegalArgumentException(m_profile_not_found);

		var oldToken = authenticateAndConsume(req.accessToken, req.clientToken, AvailableLevel.PARTIAL,
				token -> {
					if (characterToSelect != null) {
						if (token.getBoundCharacter().isPresent())
							throw newIllegalArgumentException(m_token_already_assigned);

						if (characterToSelect.getOwner() != token.getUser())
							throw newForbiddenOperationException(m_access_denied);
					}
					return true;
				});

		var newToken = tokenStore.acquireToken(oldToken.getUser(), oldToken.getClientToken(),
				characterToSelect == null ? oldToken.getBoundCharacter().orElse(null) : characterToSelect);

		var response = new LinkedHashMap<>();
		response.put("accessToken", newToken.getAccessToken());
		response.put("clientToken", newToken.getClientToken());
		newToken.getBoundCharacter().ifPresent(
				it -> response.put("selectedProfile", it.toSimpleResponse()));

		if (req.requestUser)
			response.put("user", newToken.getUser().toResponse());

		return response;
	}

	@PostMapping("/authserver/validate")
	@ResponseStatus(NO_CONTENT)
	public void validate(@RequestBody @Valid ValidateRequest req) {
		authenticate(req.accessToken, req.clientToken, AvailableLevel.COMPLETE);
	}

	@PostMapping("/authserver/invalidate")
	@ResponseStatus(NO_CONTENT)
	public void invalidate(@RequestBody @Valid InvalidateRequest req) {
		tokenStore.authenticateAndConsume(req.accessToken, null, AvailableLevel.PARTIAL, dummy -> true);
	}

	@PostMapping("/authserver/signout")
	@ResponseStatus(NO_CONTENT)
	public void signout(@RequestBody @Valid SignoutRequest req) {
		var user = passwordAuthenticated(req.username, req.password);
		tokenStore.revokeAll(user);
	}

	@PostMapping("/sessionserver/session/minecraft/join")
	@ResponseStatus(NO_CONTENT)
	public void joinServer(@RequestBody @Valid JoinServerRequest req, ServerHttpRequest http) {
		var token = authenticate(req.accessToken, null, AvailableLevel.COMPLETE);
		if (token.getBoundCharacter().isPresent() &&
				unsign(token.getBoundCharacter().get().getUuid()).equals(req.selectedProfile)) {
			var ip = of(http.getRemoteAddress())
					.map(addr -> addr.getAddress().getHostAddress());
			sessionAuth.joinServer(token, req.serverId, ip);
		} else {
			throw newForbiddenOperationException("Invalid profile.");
		}
	}

	@GetMapping("/sessionserver/session/minecraft/hasJoined")
	public ResponseEntity<?> hasJoinedServer(@RequestParam String serverId, @RequestParam String username, @RequestParam Optional<String> ip) {
		return sessionAuth.verifyUser(username, serverId, ip)
				.map(character -> ok(character.toCompleteResponse(true)))
				.orElse(noContent().build());
	}

	@PostMapping("/api/profiles/minecraft")
	public Stream<Map<?, ?>> queryProfiles(@RequestBody List<String> names) {
		return names.stream()
				.distinct()
				.map(database::findCharacterByName)
				.flatMap(Optional::stream)
				.map(YggdrasilCharacter::toSimpleResponse);
	}

	@GetMapping("/sessionserver/session/minecraft/profile/{uuid:[a-f0-9]{32}}")
	public ResponseEntity<?> profile(@PathVariable String uuid, @RequestParam(required = false) String unsigned) {
		var signed = "false".equals(unsigned);
		return database.findCharacterByUUID(toUUID(uuid))
				.map(character -> ok(character.toCompleteResponse(signed)))
				.orElse(noContent().build());
	}

	@GetMapping("/textures/{hash:[a-f0-9]{64}}")
	public ResponseEntity<?> texture(@PathVariable String hash) {
		return texturesStorage.getTexture(hash)
				.map(texture -> ok()
						.contentType(IMAGE_PNG)
						.eTag(texture.hash)
						.cacheControl(maxAge(30, DAYS).cachePublic())
						.body(texture.data))
				.orElse(notFound().build());
	}

	@DeleteMapping("/api/user/profile/{uuid}/{textureType}")
	public ResponseEntity<?> deleteTexture(@PathVariable String uuid, @PathVariable TextureType textureType, @RequestHeader(required = false) String authorization) {
		var character = authTextureOperation(uuid, textureType, authorization);
		character.getTextures().remove(textureType);
		return noContent().build();
	}

	@PutMapping("/api/user/profile/{uuid}/{textureType}")
	public ResponseEntity<?> uploadTexture(@PathVariable String uuid, @PathVariable TextureType textureType, @RequestHeader(required = false) String authorization,
			@RequestPart("file") byte[] imageFile,
			@RequestPart(name = "model", required = false) String textureModel) {
		var character = authTextureOperation(uuid, textureType, authorization);
		Texture texture;
		try (var in = new ByteArrayInputStream(imageFile)) {
			texture = texturesStorage.loadTexture(in);
		} catch (IOException e) {
			logger.warn("unable to parse uploaded texture", e);
			throw newIllegalArgumentException("bad image");
		}
		character.getTextures().put(textureType, texture);
		if (textureType == TextureType.SKIN) {
			if ("slim".equals(textureModel)) {
				character.setModel(ModelType.ALEX);
			} else {
				character.setModel(ModelType.STEVE);
			}
		}
		return noContent().build();
	}

	@ExceptionHandler(ValidationException.class)
	public void onMalformedRequest(ValidationException e) {
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, null, e);
	}

	// ---- Helper methods ----
	private YggdrasilUser passwordAuthenticated(String username, String password) {
		var user = database.findUserByEmail(username)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_credentials));

		if (!rateLimiter.tryAccess(user))
			throw newForbiddenOperationException(m_invalid_credentials);

		if (!password.equals(user.getPassword()))
			throw newForbiddenOperationException(m_invalid_credentials);

		return user;
	}

	private Token authenticate(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		return tokenStore.authenticate(accessToken, clientToken, availableLevel)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	private Token authenticateAndConsume(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel, Predicate<Token> checker) {
		return tokenStore.authenticateAndConsume(accessToken, clientToken, availableLevel, checker)
				.orElseThrow(() -> newForbiddenOperationException(m_invalid_token));
	}

	private Token processAuthorizationHeader(String header) {
		if (header != null) {
			header = header.trim();
			if (header.startsWith("Bearer ")) {
				header = header.substring("Bearer ".length());
				var token = tokenStore.authenticate(header, null, AvailableLevel.COMPLETE);
				if (token.isPresent()) {
					return token.get();
				}
			}
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
	}

	private YggdrasilCharacter authTextureOperation(String uuid, TextureType textureType, String authorization) {
		var token = processAuthorizationHeader(authorization);
		var character = database.findCharacterByUUID(toUUID(uuid))
				.orElseThrow(() -> newIllegalArgumentException(m_profile_not_found));
		if (character.getOwner() != token.getUser())
			throw newForbiddenOperationException(m_access_denied);
		if (!character.getUploadableTextures().contains(textureType))
			throw newForbiddenOperationException(m_access_denied);
		return character;
	}
	// --------

	// ---- Requests ----
	public static class LoginRequest {
		public @NotBlank String username;
		public @NotBlank String password;
		public String clientToken;
		public boolean requestUser = false;
	}

	public static class RefreshRequest {
		public @NotBlank String accessToken;
		public String clientToken;
		public boolean requestUser = false;
		public ProfileBody selectedProfile;
	}

	public static class ProfileBody {
		public @NotBlank String id;
		public @NotBlank String name;
	}

	public static class ValidateRequest {
		public @NotBlank String accessToken;
		public String clientToken;
	}

	public static class InvalidateRequest {
		public @NotBlank String accessToken;
	}

	public static class SignoutRequest {
		public @NotBlank String username;
		public @NotBlank String password;
	}

	public static class JoinServerRequest {
		public @NotBlank String accessToken;
		public @NotBlank String selectedProfile;
		public @NotBlank String serverId;
	}
	// --------

}
