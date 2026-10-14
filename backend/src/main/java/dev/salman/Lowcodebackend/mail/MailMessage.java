package dev.salman.Lowcodebackend.mail;

import java.util.UUID;

/** A plain-text email. The {@code id} makes delivery idempotent across redeliveries. */
public record MailMessage(String id, String to, String subject, String body) {

	public static MailMessage of(String to, String subject, String body) {
		return new MailMessage(UUID.randomUUID().toString(), to, subject, body);
	}

}
