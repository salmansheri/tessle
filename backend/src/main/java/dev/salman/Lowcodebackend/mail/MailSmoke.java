package dev.salman.Lowcodebackend.mail;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Manual check of real delivery: start the app with {@code TESSLE_MAIL_SMOKE_TO=you@example.com}. */
@Configuration
@ConditionalOnExpression("!'${tessle.mail.smoke-to:}'.isBlank()")
class MailSmoke {

	@Bean
	ApplicationRunner sendSmokeMail(MailQueue queue, org.springframework.core.env.Environment env) {
		return args -> queue.enqueue(MailMessage.of(env.getRequiredProperty("tessle.mail.smoke-to"),
				"Tessle mail check", "If you can read this, the mail queue and Resend SMTP are working."));
	}

}
