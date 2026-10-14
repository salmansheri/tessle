package dev.salman.Lowcodebackend.system;

import jakarta.servlet.DispatcherType;
import dev.salman.Lowcodebackend.identity.CookieBearerTokenResolver;
import dev.salman.Lowcodebackend.identity.IdentityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
class SecurityConfig {

	/**
	 * Deny by default. Sessions are stateless: the access JWT travels in an HttpOnly cookie
	 * (never in script-readable storage) and state-changing requests need the CSRF double-submit header.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityProperties props, CookieBearerTokenResolver cookieResolver) {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookieCustomizer(cookie -> cookie.secure(props.cookies().secure()).sameSite("Lax"));

		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(cookieResolver).jwt(Customizer.withDefaults()))
				.authorizeHttpRequests(auth -> auth
						// The container's internal dispatch to /error must pass, or every error status becomes a 401.
						// The original request has already been authorized (or denied) by this point.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/health", "/v3/api-docs/**", "/api/auth/csrf").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/otp/request", "/api/auth/otp/verify").permitAll()
						.anyRequest().authenticated());
		return http.build();
	}


}
