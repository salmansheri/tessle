package dev.salman.Lowcodebackend.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	User(UUID id, String email) {
		this.id = id;
		this.email = email;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

}
