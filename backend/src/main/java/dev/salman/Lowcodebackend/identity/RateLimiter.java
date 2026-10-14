package dev.salman.Lowcodebackend.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Fixed-window counter in Redis. The count and its expiry are set atomically. */
@Component
class RateLimiter {

	private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
			local n = redis.call('INCR', KEYS[1])
			if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
			return n
			""", Long.class);

	private final StringRedisTemplate redis;

	RateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** Counts one hit and returns whether it is within {@code limit} for the window. */
	boolean allow(String scope, String subject, int limit, Duration window) {
		Long count = redis.execute(HIT, List.of("rl:" + scope + ":" + digest(subject)), String.valueOf(window.toMillis()));
		return count != null && count <= limit;
	}

	/** Keys carry a digest, not the raw email or IP. */
	private static String digest(String subject) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
