
package org.to2mbn.yggdrasil.mockserver;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.Optional.ofNullable;
import static org.to2mbn.yggdrasil.mockserver.PropertiesUtils.base64Encoded;
import static org.to2mbn.yggdrasil.mockserver.PropertiesUtils.properties;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.unsign;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "yggdrasil.database", ignoreUnknownFields = false)
public class YggdrasilDatabase {

	public static enum ModelType {
		STEVE, ALEX;
	}

	public static enum TextureType {
		SKIN, CAPE, ELYTRA;
	}

	public static class YggdrasilCharacter {
		private UUID uuid;
		private String name;
		private ModelType model;
		private Map<TextureType, String> textures;
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

		public Map<TextureType, String> getTextures() {
			return textures;
		}

		public void setTextures(Map<TextureType, String> textures) {
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
						entry("textures", textures)
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
					// TODO preferredLanguage?
				))
			);
			// @formatter:on
		}
	}

	private List<YggdrasilUser> users;

	private Map<UUID, YggdrasilUser> id2user = new ConcurrentHashMap<>();
	private Map<String, YggdrasilUser> email2user = new ConcurrentHashMap<>();
	private Map<UUID, YggdrasilCharacter> uuid2character = new ConcurrentHashMap<>();
	private Map<String, YggdrasilCharacter> name2character = new ConcurrentHashMap<>();

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
		if (character.textures == null) character.textures = emptyMap();

		if (uuid2character.put(character.uuid, character) != null) throw new IllegalArgumentException("uuid conflict");
		if (name2character.put(character.name, character) != null) throw new IllegalArgumentException("name conflict");
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

	public List<YggdrasilUser> getUsers() {
		return users;
	}

	public void setUsers(List<YggdrasilUser> users) {
		this.users = users;
	}
}
