package dev.salman.Lowcodebackend.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Session lifecycle (ADR 0004). Each sign-in starts a session family. Refresh tokens rotate on every
 * use; presenting an already-used one revokes the whole family. Access tokens carry the family as
 * {@code sid} and are only valid while the family exists, so revocation is immediate.
 *
 * <p>Redis keys: {@code fam:<id>} (live family), {@code rt:<sha256(token)>} (hash: fam, user, used),
 * {@code user-fams:<userId>} (set of the user's families). Needs Redis AOF persistence, or a restart
 * signs everyone out.
 */
@Service
class TokenService {

	static final String ACCESS_COOKIE = "tessle_at";
	static final String REFRESH_COOKIE = "tessle_rt";
	static final String HINT_COOKIE = "tessle_s";
	static final String REFRESH_PATH = "/api/auth/refresh";
	static final String SESSION_CLAIM = "sid";
	static final Duration ACCESS_TTL = Duration.ofMinutes(10);
	static final Duration REFRESH_TTL = Duration.ofDays(7);

	/** Starts a family atomically. KEYS: fam, user index, token. ARGV: user, family, ttl seconds. */
	private static final DefaultRedisScript<Long> START = new DefaultRedisScript<>("""
			redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
			redis.call('SADD', KEYS[2], ARGV[2])
			redis.call('EXPIRE', KEYS[2], ARGV[3])
			redis.call('HSET', KEYS[3], 'fam', ARGV[2], 'user', ARGV[1], 'used', '0')
			redis.call('EXPIRE', KEYS[3], ARGV[3])
			return 1
			""", Long.class);

	/** Revokes every family in the user's index in one step. KEYS[1] = user index. */
	private static final DefaultRedisScript<Long> REVOKE_ALL = new DefaultRedisScript<>("""
			for _, fam in ipairs(redis.call('SMEMBERS', KEYS[1])) do redis.call('DEL', 'fam:' .. fam) end
			redis.call('DEL', KEYS[1])
			return 1
			""", Long.class);

	/**
	 * Atomically rotates a refresh token. KEYS[1] = old token key, ARGV[1] = new token key,
	 * ARGV[2] = ttl seconds. Returns {status, family, user}. The family key is derived inside the
	 * script (fine on a single Redis node; revisit if Redis Cluster is adopted).
	 */
	private static final DefaultRedisScript<List> ROTATE = new DefaultRedisScript<>("""
			local fam = redis.call('HGET', KEYS[1], 'fam')
			if not fam then return {'unknown'} end
			local famKey = 'fam:' .. fam
			if redis.call('EXISTS', famKey) == 0 then return {'revoked'} end
			if redis.call('HGET', KEYS[1], 'used') == '1' then
			  redis.call('DEL', famKey)
			  return {'reuse'}
			end
			local user = redis.call('HGET', KEYS[1], 'user')
			redis.call('HSET', KEYS[1], 'used', '1')
			redis.call('HSET', ARGV[1], 'fam', fam, 'user', user, 'used', '0')
			redis.call('EXPIRE', ARGV[1], ARGV[2])
			redis.call('EXPIRE', famKey, ARGV[2])
			redis.call('EXPIRE', 'user-fams:' .. user, ARGV[2])
			return {'ok', fam, user}
			""", List.class);

	private final JwtEncoder encoder;
	private final JwtDecoder refreshDecoder;
	private final StringRedisTemplate redis;
	private final Clock clock;
	private final boolean secureCookies;

	TokenService(JwtEncoder encoder, @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder,
			StringRedisTemplate redis, Clock clock, IdentityProperties props) {
		this.encoder = encoder;
		this.refreshDecoder = refreshDecoder;
		this.redis = redis;
		this.clock = clock;
		this.secureCookies = props.cookies().secure();
	}

	/** Starts a new session family for a freshly authenticated user. */
	ResponseCookie[] start(User user) {
		String family = UUID.randomUUID().toString();
		String userId = user.getId().toString();
		String refresh = refreshToken(userId, family);
		redis.execute(START, List.of("fam:" + family, "user-fams:" + userId, "rt:" + hash(refresh)), userId, family,
				String.valueOf(REFRESH_TTL.toSeconds()));
		return pair(userId, family, refresh);
	}

	/** Exchanges a refresh token for a new pair, or empty if it is unknown, expired, revoked or reused. */
	Optional<ResponseCookie[]> rotate(String presented) {
		Jwt jwt;
		try {
			jwt = refreshDecoder.decode(presented);
		}
		catch (JwtException e) {
			return Optional.empty();
		}
		String userId = jwt.getSubject();
		String family = jwt.getClaimAsString(SESSION_CLAIM);
		String next = refreshToken(userId, family);

		@SuppressWarnings("unchecked")
		List<String> result = redis.execute(ROTATE, List.of("rt:" + hash(presented)), "rt:" + hash(next),
				String.valueOf(REFRESH_TTL.toSeconds()));
		if (result == null || result.isEmpty() || !"ok".equals(result.get(0))) {
			return Optional.empty();
		}
		return Optional.of(pair(result.get(2), result.get(1), next));
	}

	void revoke(String family) {
		redis.delete("fam:" + family);
	}

	void revokeAll(UUID userId) {
		redis.execute(REVOKE_ALL, List.of("user-fams:" + userId));
	}

	/** Expired cookies that make the browser drop the tokens and the session hint. */
	ResponseCookie[] cleared() {
		return new ResponseCookie[] { cookie(ACCESS_COOKIE, "", "/", Duration.ZERO),
				cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO), hint(Duration.ZERO) };
	}

	private ResponseCookie[] pair(String userId, String family, String refresh) {
		Instant now = clock.instant();
		String access = sign(JwtClaimsSet.builder().issuer(IdentityConfig.ISSUER).subject(userId)
				.claim(IdentityConfig.TYPE_CLAIM, IdentityConfig.ACCESS).claim(SESSION_CLAIM, family)
				.issuedAt(now).expiresAt(now.plus(ACCESS_TTL)).id(UUID.randomUUID().toString()).build());
		return new ResponseCookie[] { cookie(ACCESS_COOKIE, access, "/", ACCESS_TTL),
				cookie(REFRESH_COOKIE, refresh, REFRESH_PATH, REFRESH_TTL), hint(REFRESH_TTL) };
	}

	/**
	 * A readable "a session may exist" flag. The refresh cookie is HttpOnly, so scripts cannot tell whether
	 * one exists; this lets signed-out visitors skip pointless refresh calls. It holds no secret.
	 */
	private ResponseCookie hint(Duration maxAge) {
		return ResponseCookie.from(HINT_COOKIE, maxAge.isZero() ? "" : "1").httpOnly(false).secure(secureCookies)
				.sameSite("Lax").path("/").maxAge(maxAge).build();
	}

	private String refreshToken(String userId, String family) {
		Instant now = clock.instant();
		return sign(JwtClaimsSet.builder().issuer(IdentityConfig.ISSUER).subject(userId)
				.claim(IdentityConfig.TYPE_CLAIM, IdentityConfig.REFRESH).claim(SESSION_CLAIM, family)
				.issuedAt(now).expiresAt(now.plus(REFRESH_TTL)).id(UUID.randomUUID().toString()).build());
	}

	private String sign(JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value).httpOnly(true).secure(secureCookies).sameSite("Lax").path(path)
				.maxAge(maxAge).build();
	}

	/** Only a digest of a refresh token is ever stored. */
	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
