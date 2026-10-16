package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Credentials encryption, and the rule that the app does not start without a usable master key. */
class SecretBoxTests {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private final ApplicationContextRunner app = new ApplicationContextRunner()
			.withUserConfiguration(DataSourceConfig.class, SecretBox.class);

	@Test
	void theAppRefusesToStartWithoutAMasterKey() {
		app.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void theAppRefusesToStartWithAKeyThatIsNotAes256() {
		app.withPropertyValues("tessle.secrets.master-key=" + Base64.getEncoder().encodeToString(new byte[16]))
				.run(context -> assertThat(context).hasFailed());
		app.withPropertyValues("tessle.secrets.master-key=not base64 at all!")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void aSealedSecretOpensOnlyWithItsOwnContextAndNeverContainsThePlaintext() {
		app.withPropertyValues("tessle.secrets.master-key=" + KEY).run(context -> {
			SecretBox box = context.getBean(SecretBox.class);
			SecretBox.Sealed sealed = box.seal("hunter2-hunter2", "row-a");

			assertThat(sealed.keyId()).isEqualTo("k1");
			assertThat(new String(sealed.ciphertext(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("hunter2");
			assertThat(box.open(sealed.ciphertext(), sealed.keyId(), "row-a")).isEqualTo("hunter2-hunter2");
			// Copied to another row, or sealed by an unknown key: refused.
			assertThatThrownBy(() -> box.open(sealed.ciphertext(), sealed.keyId(), "row-b")).isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(() -> box.open(sealed.ciphertext(), "k2", "row-a")).isInstanceOf(IllegalStateException.class);
			// The same secret sealed twice differs (fresh IV each time).
			assertThat(box.seal("hunter2-hunter2", "row-a").ciphertext()).isNotEqualTo(sealed.ciphertext());
		});
	}

}
