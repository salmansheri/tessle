package dev.salman.Lowcodebackend.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The credentials master key. Validated at startup: the app refuses to run without a usable key. */
@Validated
@ConfigurationProperties("tessle.secrets")
public record SecretsProperties(@NotBlank String masterKey, @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,32}") String keyId) {

	public SecretsProperties {
		if (keyId == null || keyId.isBlank()) {
			keyId = "k1";
		}
	}

}
