package dev.salman.Lowcodebackend.system;

import dev.salman.Lowcodebackend.identity.CookieBearerTokenResolver;
import dev.salman.Lowcodebackend.identity.IdentityProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
class SecurityConfig {

	/**
	 * Deny by default. Sessions are stateless: the access JWT travels in an HttpOnly cookie
	 * (never in script-readable storage) and state-changing requests need the CSRF double-submit header.
	 *
	 * <p>The bearer-token filter is wired by hand, not through {@code oauth2ResourceServer()}. That DSL
	 * exempts every request that carries a bearer token from CSRF checks, which is right for
	 * Authorization headers but would switch CSRF off for every cookie-authenticated request here.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityProperties props,
			CookieBearerTokenResolver cookieResolver, JwtDecoder jwtDecoder) {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookieCustomizer(cookie -> cookie.secure(props.cookies().secure()).sameSite("Lax"));

		BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
		BearerTokenAuthenticationFilter bearerFilter = new BearerTokenAuthenticationFilter(
				new ProviderManager(new JwtAuthenticationProvider(jwtDecoder)));
		bearerFilter.setBearerTokenResolver(cookieResolver);
		bearerFilter.setAuthenticationEntryPoint(entryPoint);

		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
				.addFilterBefore(bearerFilter, AuthorizationFilter.class)
				.authorizeHttpRequests(auth -> auth
						// The container's internal dispatch to /error must pass, or every error status becomes a 401.
						// The original request has already been authorized (or denied) by this point.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/health", "/v3/api-docs/**", "/api/auth/csrf").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/otp/request", "/api/auth/otp/verify", "/api/auth/refresh")
						.permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

}
