package dev.salman.Lowcodebackend.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "otp_codes")
class OtpCode {

	@Id
	private UUID id;

	private String email;

	@Column(name = "code_hash")
	private String codeHash;

	private int attempts;

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected OtpCode() {
	}

	UUID getId() {
		return id;
	}

	String getCodeHash() {
		return codeHash;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

}
