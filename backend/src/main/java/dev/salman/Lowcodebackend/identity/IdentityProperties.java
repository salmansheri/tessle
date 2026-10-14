package dev.salman.Lowcodebackend.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Secrets are validated at startup: the app refuses to run with a missing or short one. */
@Validated
@ConfigurationProperties("tessle")
public record IdentityProperties(@Valid Secret jwt, @Valid Secret otp, Cookies cookies) {

	public record Secret(@NotBlank @Size(min = 32) String secret) {
	}

	public record Cookies(boolean secure) {
	}

}
