package dev.salman.Lowcodebackend.mail;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Sends queued mail. Failures propagate so the listener's retry policy applies; once retries
 * are exhausted the message is rejected and dead-lettered by the broker.
 */
@Component
class MailConsumer {

	private static final Logger log = LoggerFactory.getLogger(MailConsumer.class);
	private static final Duration SENT_MARKER_TTL = Duration.ofDays(7);

	private final JavaMailSender sender;
	private final StringRedisTemplate redis;
	private final String from;

	MailConsumer(JavaMailSender sender, StringRedisTemplate redis, @Value("${tessle.mail.from}") String from) {
		this.sender = sender;
		this.redis = redis;
		this.from = from;
	}

	@RabbitListener(queues = MailConfig.QUEUE)
	void deliver(MailMessage message, @Header(name = MailQueue.TRACE_ID, required = false) String traceId) {
		if (traceId != null) {
			MDC.put(MailQueue.TRACE_ID, traceId);
		}
		try {
			if (message.id() == null || message.id().isBlank() || message.to() == null || message.to().isBlank()) {
				// Retrying cannot fix a malformed message; send it straight to the dead-letter queue.
				throw new AmqpRejectAndDontRequeueException("Mail message needs an id and a recipient");
			}
			String sentKey = "mail:sent:" + message.id();
			if (Boolean.TRUE.equals(redis.hasKey(sentKey))) {
				log.info("Mail {} already sent, skipping redelivery", message.id());
				return;
			}
			SimpleMailMessage mail = new SimpleMailMessage();
			mail.setFrom(from);
			mail.setTo(message.to());
			mail.setSubject(message.subject());
			mail.setText(message.body());
			sender.send(mail);
			redis.opsForValue().set(sentKey, "1", SENT_MARKER_TTL);
			log.info("Mail {} sent", message.id());
		}
		finally {
			MDC.remove(MailQueue.TRACE_ID);
		}
	}

}
