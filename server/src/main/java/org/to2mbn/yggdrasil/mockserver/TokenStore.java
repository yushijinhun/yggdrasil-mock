package org.to2mbn.yggdrasil.mockserver;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.to2mbn.yggdrasil.mockserver.UUIDUtils.randomUnsignedUUID;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.m_access_denied;
import static org.to2mbn.yggdrasil.mockserver.exception.YggdrasilException.newForbiddenOperationException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.to2mbn.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilCharacter;
import org.to2mbn.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilUser;

@Component
@ConfigurationProperties(prefix = "yggdrasil.token", ignoreUnknownFields = false)
public class TokenStore {

	public class Token {
		private String clientToken;
		private String accessToken;
		private long createdAt;
		private Optional<YggdrasilCharacter> boundCharacter;
		private YggdrasilUser user;
		private boolean revoked;

		private Token() {}

		public boolean isValid() {
			if (isFullyExpired())
				return false;
			if (enableTimeToPartiallyExpired && System.currentTimeMillis() > createdAt + timeToPartiallyExpired.toMillis())
				return false;
			if (onlyLastSessionAvailable && this != lastAcquiredToken.get(user))
				return false;
			return true;
		}

		public boolean isRefreshable() {
			if (isFullyExpired())
				return false;
			return true;
		}

		private boolean isFullyExpired() {
			return revoked || System.currentTimeMillis() > createdAt + timeToFullyExpired.toMillis();
		}

		public String getClientToken() {
			return clientToken;
		}

		public String getAccessToken() {
			return accessToken;
		}

		public long getCreatedAt() {
			return createdAt;
		}

		public Optional<YggdrasilCharacter> getBoundCharacter() {
			return boundCharacter;
		}

		public YggdrasilUser getUser() {
			return user;
		}

		public boolean isRevoked() {
			return revoked;
		}

		public void revoke() {
			if (!revoked) {
				revoked = true;
				removeToken(this);
			}
		}
	}

	private Duration timeToFullyExpired;

	private boolean enableTimeToPartiallyExpired;
	private Duration timeToPartiallyExpired;

	private boolean onlyLastSessionAvailable;

	private Map<String, Token> accessToken2token = new ConcurrentHashMap<>();
	private Map<YggdrasilUser, Token> lastAcquiredToken = new ConcurrentHashMap<>();

	private void removeToken(Token token) {
		accessToken2token.remove(token.accessToken);
		lastAcquiredToken.remove(token.user, token);
	}

	public Optional<Token> findToken(String accessToken) {
		Token token = accessToken2token.get(accessToken);
		if (token != null && !token.isRefreshable() && !token.isValid()) {
			removeToken(token);
			return empty();
		}
		return ofNullable(token);
	}

	public Token acquireToken(YggdrasilUser user, @Nullable String clientToken, @Nullable YggdrasilCharacter selectedCharacter) {
		Token token = new Token();
		token.accessToken = randomUnsignedUUID();
		if (selectedCharacter == null) {
			if (user.getCharacters().size() == 1) {
				token.boundCharacter = of(user.getCharacters().get(0));
			} else {
				token.boundCharacter = empty();
			}
		} else {
			if (!user.getCharacters().contains(selectedCharacter)) {
				throw newForbiddenOperationException(m_access_denied);
			}
			token.boundCharacter = of(selectedCharacter);
		}
		token.clientToken = clientToken == null ? randomUnsignedUUID() : clientToken;
		token.createdAt = System.currentTimeMillis();
		token.revoked = false;
		token.user = user;
		accessToken2token.put(token.accessToken, token);
		lastAcquiredToken.put(user, token);
		return token;
	}

	public void revokeAll(YggdrasilUser user) {
		accessToken2token.values().stream()
				.filter(token -> token.user == user)
				.forEach(Token::revoke);
	}

	public Duration getTimeToPartiallyExpired() {
		return timeToPartiallyExpired;
	}

	public void setTimeToPartiallyExpired(Duration timeToPartiallyExpired) {
		this.timeToPartiallyExpired = timeToPartiallyExpired;
	}

	public Duration getTimeToFullyExpired() {
		return timeToFullyExpired;
	}

	public void setTimeToFullyExpired(Duration timeToFullyExpired) {
		this.timeToFullyExpired = timeToFullyExpired;
	}

	public boolean isEnableTimeToPartiallyExpired() {
		return enableTimeToPartiallyExpired;
	}

	public void setEnableTimeToPartiallyExpired(boolean enableTimeToPartiallyExpired) {
		this.enableTimeToPartiallyExpired = enableTimeToPartiallyExpired;
	}

	public boolean isOnlyLastSessionAvailable() {
		return onlyLastSessionAvailable;
	}

	public void setOnlyLastSessionAvailable(boolean onlyLastSessionAvailable) {
		this.onlyLastSessionAvailable = onlyLastSessionAvailable;
	}
}
