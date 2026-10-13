package dev.salman.Lowcodebackend.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

	/** Deny by default; only the health check and the OpenAPI spec are public. */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/health", "/v3/api-docs/**").permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

}
