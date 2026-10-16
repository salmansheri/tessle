package dev.salman.Lowcodebackend.datasource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for stored credentials. Each secret is stored with the id of the key that sealed it,
 * so keys can be rotated later; today there is a single key from the environment. The ciphertext is bound to
 * its owner through authenticated data, so a value copied to another row fails to open.
 */
@Component
public class SecretBox {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	/** What to store: the sealed bytes and the id of the key used. */
	public record Sealed(byte[] ciphertext, String keyId) {
	}

	private final SecretKeySpec key;
	private final String keyId;
	private final SecureRandom random = new SecureRandom();

	SecretBox(SecretsProperties properties) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(properties.masterKey().trim());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("tessle.secrets.master-key must be base64", e);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("tessle.secrets.master-key must decode to exactly 32 bytes (AES-256), got " + raw.length);
		}
		this.key = new SecretKeySpec(raw, "AES");
		this.keyId = properties.keyId();
	}

	public Sealed seal(String plaintext, String context) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[IV_BYTES + sealed.length];
			System.arraycopy(iv, 0, out, 0, IV_BYTES);
			System.arraycopy(sealed, 0, out, IV_BYTES, sealed.length);
			return new Sealed(out, keyId);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("could not encrypt a secret", e);
		}
	}

	public String open(byte[] stored, String storedKeyId, String context) {
		if (!keyId.equals(storedKeyId)) {
			throw new IllegalStateException("no key available for key id " + storedKeyId);
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
			cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("could not decrypt a secret", e);
		}
	}

}
