
package moe.yushi.yggdrasil.mockserver;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static moe.yushi.yggdrasil.mockserver.PropertiesUtils.base64Encoded;
import static moe.yushi.yggdrasil.mockserver.PropertiesUtils.properties;
import static moe.yushi.yggdrasil.mockserver.UUIDUtils.unsign;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;

@Component
@ConfigurationProperties(prefix = "yggdrasil.database", ignoreUnknownFields = false)
public class YggdrasilDatabase {

	public static enum ModelType {
		STEVE("default"),
		ALEX("slim");

		private String modelName;

		ModelType(String modelName) {
			this.modelName = modelName;
		}

		public String getModelName() {
			return modelName;
		}
	}

	public static enum TextureType {
		SKIN(character -> of(singletonMap("model", character.getModel().getModelName()))),
		CAPE,
		ELYTRA;

		private Function<YggdrasilCharacter, Optional<Map<?, ?>>> metadataFunc;

		TextureType() {
			this(dummy -> empty());
		}

		TextureType(Function<YggdrasilCharacter, Optional<Map<?, ?>>> metadataFunc) {
			this.metadataFunc = metadataFunc;
		}

		public Optional<Map<?, ?>> getMetadata(YggdrasilCharacter character) {
			return metadataFunc.apply(character);
		}
	}

	public static class YggdrasilCharacter {
		private UUID uuid;
		private String name;
		private ModelType model;
		private Map<TextureType, String> texturesLocations;
		private Map<TextureType, Texture> textures;
		private YggdrasilUser owner;

		public UUID getUuid() {
			return uuid;
		}

		public void setUuid(UUID uuid) {
			this.uuid = uuid;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public ModelType getModel() {
			return model;
		}

		public void setModel(ModelType model) {
			this.model = model;
		}

		public Map<TextureType, String> getTexturesLocations() {
			return texturesLocations;
		}

		public void setTexturesLocations(Map<TextureType, String> texturesLocations) {
			this.texturesLocations = texturesLocations;
		}

		public Map<TextureType, Texture> getTextures() {
			return textures;
		}

		public void setTextures(Map<TextureType, Texture> textures) {
			this.textures = textures;
		}

		public YggdrasilUser getOwner() {
			return owner;
		}

		public void setOwner(YggdrasilUser owner) {
			this.owner = owner;
		}

		public Map<String, Object> toSimpleResponse() {
			return
			// @formatter:off
			ofEntries(
				entry("id", unsign(uuid)),
				entry("name", name)
			);
			// @formatter:on
		}

		public Map<String, Object> toCompleteResponse(boolean signed) {
			var texturesResponse = new LinkedHashMap<>();
			textures.forEach((type, texture) -> {
				// @formatter:off
				texturesResponse.put(type, type.getMetadata(this)
					.map(metadata -> ofEntries(
						entry("url", texture.url),
						entry("metadata", metadata)
					))
					.orElseGet(() -> singletonMap("url", texture.url))
				);
				// @formatter:on
			});

			return
			// @formatter:off
			ofEntries(
				entry("id", unsign(uuid)),
				entry("name", name),
				entry("properties", properties(signed,
					entry("textures", base64Encoded(
						entry("timestamp", System.currentTimeMillis()),
						entry("profileId", unsign(uuid)),
						entry("profileName", name),
						entry("textures", texturesResponse)
					))
				))
			);
			// @formatter:on
		}
	}

	public static class YggdrasilUser {
		private UUID id;
		private String email;
		private String password;
		private List<YggdrasilCharacter> characters;

		public UUID getId() {
			return id;
		}

		public void setId(UUID id) {
			this.id = id;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public List<YggdrasilCharacter> getCharacters() {
			return characters;
		}

		public void setCharacters(List<YggdrasilCharacter> characters) {
			this.characters = characters;
		}

		public Map<String, Object> toResponse() {
			return
			// @formatter:off
			ofEntries(
				entry("id", unsign(id)),
				entry("properties", properties(
				))
			);
			// @formatter:on
		}
	}

	public static class Texture {
		private String hash;
		private byte[] data;
		private String url;

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}

		public byte[] getData() {
			return data;
		}

		public void setData(byte[] data) {
			this.data = data;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public static String computeTextureId(BufferedImage img) {
			MessageDigest digest;
			try {
				digest = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			int width = img.getWidth();
			int height = img.getHeight();
			byte[] buf = new byte[4096];

			putInt(buf, 0, width);
			putInt(buf, 4, height);
			int pos = 8;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					putInt(buf, pos, img.getRGB(x, y));
					if (buf[pos + 0] == 0) {
						buf[pos + 1] = buf[pos + 2] = buf[pos + 3] = 0;
					}
					pos += 4;
					if (pos == buf.length) {
						pos = 0;
						digest.update(buf, 0, buf.length);
					}
				}
			}
			if (pos > 0) {
				digest.update(buf, 0, pos);
			}

			byte[] sha256 = digest.digest();
			return String.format("%0" + (sha256.length << 1) + "x", new BigInteger(1, sha256));
		}

		private static void putInt(byte[] array, int offset, int x) {
			array[offset + 0] = (byte) (x >> 24 & 0xff);
			array[offset + 1] = (byte) (x >> 16 & 0xff);
			array[offset + 2] = (byte) (x >> 8 & 0xff);
			array[offset + 3] = (byte) (x >> 0 & 0xff);
		}
	}

	private List<YggdrasilUser> users;

	private Map<UUID, YggdrasilUser> id2user = new ConcurrentHashMap<>();
	private Map<String, YggdrasilUser> email2user = new ConcurrentHashMap<>();
	private Map<UUID, YggdrasilCharacter> uuid2character = new ConcurrentHashMap<>();
	private Map<String, YggdrasilCharacter> name2character = new ConcurrentHashMap<>();
	private Map<String, Texture> hash2texture = new ConcurrentHashMap<>();

	@Value("#{rootUrl}")
	private Supplier<UriBuilder> rootUrl;

	@Autowired
	private ApplicationContext ctx;

	@PostConstruct
	private void buildDatabase() {
		users.forEach(user -> {
			try {
				processUser(user);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("error while processing user " + user.email, e);
			}
		});
	}

	private void processUser(YggdrasilUser user) {
		if (user.id == null) user.id = UUID.randomUUID();
		if (user.email == null) throw new IllegalArgumentException("email is missing");
		if (user.password == null || user.password.isEmpty()) throw new IllegalArgumentException("password is missing");
		if (user.characters == null) user.characters = emptyList();

		if (id2user.put(user.id, user) != null) throw new IllegalArgumentException("id conflict");
		if (email2user.put(user.email, user) != null) throw new IllegalArgumentException("email conflict");

		user.characters.forEach(character -> {
			try {
				processCharacter(character, user);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("error while processing character " + character.name, e);
			}
		});
	}

	private void processCharacter(YggdrasilCharacter character, YggdrasilUser owner) {
		if (character.owner != null) throw new IllegalArgumentException("owner has already been set");
		character.owner = owner;
		if (character.uuid == null) character.uuid = UUID.randomUUID();
		if (character.name == null) throw new IllegalArgumentException("name is missing");
		if (character.model == null) character.model = ModelType.STEVE;
		if (character.texturesLocations == null) character.texturesLocations = emptyMap();

		character.textures = new LinkedHashMap<>();
		character.texturesLocations.forEach((type, location) -> character.textures.put(type, processTexture(location)));

		if (uuid2character.put(character.uuid, character) != null) throw new IllegalArgumentException("uuid conflict");
		if (name2character.put(character.name, character) != null) throw new IllegalArgumentException("name conflict");
	}

	private Texture processTexture(String location) {
		BufferedImage img;
		try (var in = ctx.getResource(location).getInputStream()) {
			img = ImageIO.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return hash2texture.computeIfAbsent(Texture.computeTextureId(img), hash -> {
			var texture = new Texture();
			texture.hash = hash;
			var bout = new ByteArrayOutputStream();
			try {
				ImageIO.write(img, "png", bout);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			texture.data = bout.toByteArray();
			texture.url = rootUrl.get().path("/textures/{hash}").build(hash).toString();
			return texture;
		});
	}

	public Optional<YggdrasilUser> findUserById(UUID id) {
		return ofNullable(id2user.get(id));
	}

	public Optional<YggdrasilUser> findUserByEmail(String email) {
		return ofNullable(email2user.get(email));
	}

	public Optional<YggdrasilCharacter> findCharacterByUUID(UUID uuid) {
		return ofNullable(uuid2character.get(uuid));
	}

	public Optional<YggdrasilCharacter> findCharacterByName(String name) {
		return ofNullable(name2character.get(name));
	}

	public Optional<Texture> findTextureByHash(String hash) {
		return ofNullable(hash2texture.get(hash));
	}

	public List<YggdrasilUser> getUsers() {
		return users;
	}

	public void setUsers(List<YggdrasilUser> users) {
		this.users = users;
	}
}
