package org.to2mbn.yggdrasil.mockserver;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

		public boolean isValid() {
			return !revoked && System.currentTimeMillis() < createdAt + timeToPartiallyExpired.toMillis();
		}

		public boolean isRefreshable() {
			return !revoked && System.currentTimeMillis() < createdAt + timeToFullyExpired.toMillis();
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
	}

	private Duration timeToPartiallyExpired;
	private Duration timeToFullyExpired;

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
}
