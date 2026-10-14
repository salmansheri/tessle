package dev.salman.Lowcodebackend.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

	Optional<OtpCode> findByEmail(String email);

	/** Replaces any existing code for the email, which invalidates it. */
	@Modifying
	@Query(value = """
			insert into otp_codes (id, email, code_hash, attempts, created_at, expires_at)
			values (:id, :email, :hash, 0, :now, :expiresAt)
			on conflict (email) do update
			set id = excluded.id, code_hash = excluded.code_hash, attempts = 0,
			    created_at = excluded.created_at, expires_at = excluded.expires_at
			""", nativeQuery = true)
	void upsert(UUID id, String email, String hash, Instant now, Instant expiresAt);

	/** Atomically spends one attempt; returns 0 when the attempts are used up. */
	@Modifying
	@Query(value = "update otp_codes set attempts = attempts + 1 where id = :id and attempts < :max", nativeQuery = true)
	int spendAttempt(UUID id, int max);

	/** Atomically consumes the code; returns 0 if it was already consumed. */
	@Modifying
	@Query(value = "delete from otp_codes where id = :id", nativeQuery = true)
	int consume(UUID id);

	/** Housekeeping: anyone can request a code, so expired rows must not pile up. */
	@Modifying
	@Query(value = "delete from otp_codes where expires_at < :cutoff", nativeQuery = true)
	int deleteExpiredBefore(Instant cutoff);

}
