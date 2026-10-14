package dev.salman.Lowcodebackend.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import dev.salman.Lowcodebackend.mail.MailMessage;
import dev.salman.Lowcodebackend.mail.MailQueue;
import dev.salman.Lowcodebackend.system.AfterCommit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OtpService {

	static final Duration TTL = Duration.ofMinutes(10);
	static final int MAX_ATTEMPTS = 5;

	private final OtpCodeRepository codes;
	private final UserRepository users;
	private final MailQueue mailQueue;
	private final Clock clock;
	private final SecretKeySpec hmacKey;
	private final SecureRandom random = new SecureRandom();

	OtpService(OtpCodeRepository codes, UserRepository users, MailQueue mailQueue, Clock clock, IdentityProperties props) {
		this.codes = codes;
		this.users = users;
		this.mailQueue = mailQueue;
		this.clock = clock;
		this.hmacKey = new SecretKeySpec(props.otp().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	/** Issues a code for any email alike, so callers cannot tell which emails have accounts. */
	@Transactional
	void request(String email) {
		String code = String.format("%06d", random.nextInt(1_000_000));
		Instant now = clock.instant();
		codes.upsert(UUID.randomUUID(), email, hash(email, code), now, now.plus(TTL));
		MailMessage mail = MailMessage.of(email, "Your Tessle sign-in code",
				"Your Tessle sign-in code is " + code + ". It expires in 10 minutes. "
						+ "If you did not ask for it, you can ignore this email.");
		AfterCommit.run(() -> mailQueue.enqueue(mail));
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	@Transactional
	void deleteStaleCodes() {
		codes.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
	}

	/** Returns the signed-in User, or empty for any kind of failure (wrong, expired, locked, unknown). */
	@Transactional
	Optional<User> verify(String email, String code) {
		Optional<OtpCode> stored = codes.findByEmail(email);
		if (stored.isEmpty() || !stored.get().getExpiresAt().isAfter(clock.instant())) {
			return Optional.empty();
		}
		OtpCode otp = stored.get();
		if (codes.spendAttempt(otp.getId(), MAX_ATTEMPTS) == 0) {
			return Optional.empty();
		}
		boolean matches = MessageDigest.isEqual(hash(email, code).getBytes(StandardCharsets.UTF_8),
				otp.getCodeHash().getBytes(StandardCharsets.UTF_8));
		if (!matches || codes.consume(otp.getId()) == 0) {
			return Optional.empty();
		}
		users.insertIfAbsent(UUID.randomUUID(), email);
		return users.findByEmail(email);
	}

	/** HMAC with a server secret: a stolen table of 6-digit hashes cannot be brute-forced offline. */
	private String hash(String email, String code) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(hmacKey);
			return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
