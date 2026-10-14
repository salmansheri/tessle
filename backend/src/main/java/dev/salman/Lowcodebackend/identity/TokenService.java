package dev.salman.Lowcodebackend.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues the access/refresh cookie pair (ADR 0004). Refresh rotation and revocation build on the Redis record. */
@Service
class TokenService {

	static final String ACCESS_COOKIE = "tessle_at";
	static final String REFRESH_COOKIE = "tessle_rt";
	static final String REFRESH_PATH = "/api/auth/refresh";
	static final Duration ACCESS_TTL = Duration.ofMinutes(10);
	static final Duration REFRESH_TTL = Duration.ofDays(7);

	private final JwtEncoder encoder;
	private final StringRedisTemplate redis;
	private final Clock clock;
	private final boolean secureCookies;

	TokenService(JwtEncoder encoder, StringRedisTemplate redis, Clock clock, IdentityProperties props) {
		this.encoder = encoder;
		this.redis = redis;
		this.clock = clock;
		this.secureCookies = props.cookies().secure();
	}

	/** The active Workspace id ({@code wid}) is added once Workspaces exist. */
	ResponseCookie[] issue(User user) {
		Instant now = clock.instant();
		String access = sign(JwtClaimsSet.builder().issuer(IdentityConfig.ISSUER).subject(user.getId().toString())
				.claim(IdentityConfig.TYPE_CLAIM, IdentityConfig.ACCESS).issuedAt(now).expiresAt(now.plus(ACCESS_TTL))
				.id(UUID.randomUUID().toString()).build());

		String family = UUID.randomUUID().toString();
		String refresh = sign(JwtClaimsSet.builder().issuer(IdentityConfig.ISSUER).subject(user.getId().toString())
				.claim(IdentityConfig.TYPE_CLAIM, IdentityConfig.REFRESH).issuedAt(now).expiresAt(now.plus(REFRESH_TTL))
				.id(family).build());
		redis.opsForValue().set("refresh:" + family, user.getId().toString(), REFRESH_TTL);

		return new ResponseCookie[] { cookie(ACCESS_COOKIE, access, "/", ACCESS_TTL),
				cookie(REFRESH_COOKIE, refresh, REFRESH_PATH, REFRESH_TTL) };
	}

	private String sign(JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value).httpOnly(true).secure(secureCookies).sameSite("Lax").path(path)
				.maxAge(maxAge).build();
	}

}
