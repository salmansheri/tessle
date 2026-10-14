package dev.salman.Lowcodebackend.mail;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** Entry point for sending email: enqueue and return. Delivery happens in {@link MailConsumer}. */
@Service
public class MailQueue {

	static final String TRACE_ID = "traceId";

	private final RabbitTemplate rabbit;

	MailQueue(RabbitTemplate rabbit) {
		this.rabbit = rabbit;
	}

	public void enqueue(MailMessage message) {
		String traceId = MDC.get(TRACE_ID);
		rabbit.convertAndSend(MailConfig.EXCHANGE, MailConfig.ROUTING_KEY, message, amqp -> {
			if (traceId != null) {
				amqp.getMessageProperties().setHeader(TRACE_ID, traceId);
			}
			return amqp;
		});
	}

}
