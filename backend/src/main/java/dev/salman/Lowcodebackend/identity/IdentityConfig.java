package dev.salman.Lowcodebackend.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

	static final String ISSUER = "tessle";
	static final String TYPE_CLAIM = "typ";
	static final String ACCESS = "access";
	static final String REFRESH = "refresh";

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtKey(IdentityProperties props) {
		return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	/** Only accepts live access tokens: right type, and the session family must still exist (instant sign-out). */
	@Bean
	@Primary
	JwtDecoder jwtDecoder(SecretKey jwtKey, StringRedisTemplate redis) {
		OAuth2TokenValidator<Jwt> sessionAlive = jwt -> {
			String sid = jwt.getClaimAsString(TokenService.SESSION_CLAIM);
			return sid != null && Boolean.TRUE.equals(redis.hasKey("fam:" + sid)) ? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session has ended", null));
		};
		return decoder(jwtKey, ACCESS, sessionAlive);
	}

	/** Only accepts refresh tokens, so an access token cannot be used to refresh. */
	@Bean
	JwtDecoder refreshJwtDecoder(SecretKey jwtKey) {
		return decoder(jwtKey, REFRESH);
	}

	@SafeVarargs
	private static JwtDecoder decoder(SecretKey key, String type, OAuth2TokenValidator<Jwt>... extra) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
		validators.add(JwtValidators.createDefaultWithIssuer(ISSUER));
		validators.add(new JwtClaimValidator<String>(TYPE_CLAIM, type::equals));
		validators.addAll(List.of(extra));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
		return decoder;
	}


}
