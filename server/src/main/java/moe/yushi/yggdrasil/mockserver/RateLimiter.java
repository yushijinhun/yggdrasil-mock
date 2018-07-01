package moe.yushi.yggdrasil.mockserver;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import moe.yushi.yggdrasil.mockserver.YggdrasilDatabase.YggdrasilUser;

@Component
@ConfigurationProperties(prefix = "yggdrasil.rate-limit", ignoreUnknownFields = false)
public class RateLimiter {

	private Map<YggdrasilUser, AtomicLong> timings = new ConcurrentHashMap<>();

	private Duration limitDuration;

	public boolean tryAccess(YggdrasilUser key) {
		AtomicLong v1 = timings.get(key);
		long now = System.currentTimeMillis();

		if (v1 == null) {
			// if putIfAbsent() returns a non-null value,
			// which means another thread who is also trying to access this key
			// has put an AtomicLong into the map between our last get() call and this putIfAbsent() call,
			// this access must be rate-limited, as the duration between two calls is really a short time
			// (and the value of the AtomicLong is within the duration).
			return timings.putIfAbsent(key, new AtomicLong(now)) == null;
		}

		long last = v1.get();
		if (now - last > limitDuration.toMillis()) {
			// same as above
			// if the CAS operation fails, this access must be rate-limited.
			return v1.compareAndSet(last, now);
		} else {
			return false;
		}
	}

	public Duration getLimitDuration() {
		return limitDuration;
	}

	public void setLimitDuration(Duration limitDuration) {
		this.limitDuration = limitDuration;
	}
}
