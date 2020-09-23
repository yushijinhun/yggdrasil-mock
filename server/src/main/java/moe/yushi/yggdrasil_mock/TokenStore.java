package moe.yushi.yggdrasil_mock;

import static java.lang.Math.max;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.yggdrasil_mock.UUIDUtils.randomUnsignedUUID;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.YggdrasilDatabase.YggdrasilUser;

@Component
@ConfigurationProperties(prefix = "yggdrasil.token")
public class TokenStore {

	private static final int MAX_TOKEN_COUNT = 100_000;

	public static enum AvailableLevel {
		COMPLETE, PARTIAL;
	}

	public class Token {
		private long id;
		private String clientToken;
		private String accessToken;
		private long createdAt;
		private Optional<YggdrasilCharacter> boundCharacter;
		private YggdrasilUser user;

		private Token() {}

		/** Assuming isFullyExpired() returns true */
		private boolean isCompleteValid() {
			if (enableTimeToPartiallyExpired && System.currentTimeMillis() > createdAt + timeToPartiallyExpired.toMillis())
				return false;
			if (onlyLastSessionAvailable && this != lastAcquiredToken.get(user))
				return false;
			return true;
		}

		private boolean isFullyExpired() {
			if (System.currentTimeMillis() > createdAt + timeToFullyExpired.toMillis())
				return true;

			AtomicLong latestRevoked = notBefore.get(user);
			if (latestRevoked != null && id < latestRevoked.get())
				return true;

			return false;
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
	}

	private Duration timeToFullyExpired;

	private boolean enableTimeToPartiallyExpired;
	private Duration timeToPartiallyExpired;

	private boolean onlyLastSessionAvailable;

	private AtomicLong tokenIdGen = new AtomicLong();
	private ConcurrentHashMap<YggdrasilUser, AtomicLong> notBefore = new ConcurrentHashMap<>();
	private ConcurrentHashMap<YggdrasilUser, Token> lastAcquiredToken = new ConcurrentHashMap<>();
	private ConcurrentLinkedHashMap<String, Token> accessToken2token = new ConcurrentLinkedHashMap.Builder<String, Token>()
			.maximumWeightedCapacity(MAX_TOKEN_COUNT)
			.listener((k, v) -> lastAcquiredToken.remove(v.user, v))
			.build();

	private void removeToken(Token token) {
		accessToken2token.remove(token.accessToken);
		lastAcquiredToken.remove(token.user, token);
	}

	public Optional<Token> authenticate(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		var token = accessToken2token.getQuietly(accessToken);
		if (token == null)
			return empty();

		if (token.isFullyExpired()) {
			removeToken(token);
			return empty();
		}

		if (clientToken != null && !clientToken.equals(token.clientToken))
			return empty();

		switch (availableLevel) {
			case COMPLETE:
				if (token.isCompleteValid()) {
					return of(token);
				} else {
					return empty();
				}

			case PARTIAL:
				return of(token);

			default:
				throw new IllegalArgumentException("Unknown AvailableLevel: " + availableLevel);
		}
	}

	/**
	 * @param checker
	 *            returning false will cause the method to return empty, throwing an exception is also ok
	 */
	public Optional<Token> authenticateAndConsume(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel, Predicate<Token> checker) {
		return authenticate(accessToken, clientToken, availableLevel)
				.flatMap(token -> {
					if (!checker.test(token))
						// the operation cannot be performed
						return empty();

					if (accessToken2token.remove(accessToken) == token) {
						// we have won the remove() race
						lastAcquiredToken.remove(token.user, token);
						return of(token);
					} else {
						// another thread won the race and consumed the token
						return empty();
					}
				});
	}

	public Token acquireToken(YggdrasilUser user, @Nullable String clientToken, @Nullable YggdrasilCharacter selectedCharacter) {
		var token = new Token();
		token.accessToken = randomUnsignedUUID();
		if (selectedCharacter == null) {
			if (user.getCharacters().size() == 1) {
				token.boundCharacter = of(user.getCharacters().get(0));
			} else {
				token.boundCharacter = empty();
			}
		} else {
			if (!user.getCharacters().contains(selectedCharacter)) {
				throw new IllegalArgumentException("the character to select doesn't belong to the user");
			}
			token.boundCharacter = of(selectedCharacter);
		}
		token.clientToken = clientToken == null ? randomUnsignedUUID() : clientToken;
		token.createdAt = System.currentTimeMillis();
		token.user = user;
		token.id = tokenIdGen.getAndIncrement();

		accessToken2token.put(token.accessToken, token);
		// the token we just put into `accessToken2token` may have been flush out from cache here,
		// and the listener may be notified before the token is put into `lastAcquiredToken`
		lastAcquiredToken.put(user, token);

		if (!accessToken2token.containsKey(token.accessToken))
			// if so, remove the token from `lastAcquiredToken`
			lastAcquiredToken.remove(user, token);

		return token;
	}

	public void revokeAll(YggdrasilUser user) {
		notBefore.computeIfAbsent(user, k -> new AtomicLong())
				.getAndUpdate(original -> max(original, tokenIdGen.get()));
	}

	public int tokensCount() {
		return accessToken2token.size();
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
