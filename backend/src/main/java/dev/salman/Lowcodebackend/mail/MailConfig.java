package dev.salman.Lowcodebackend.mail;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {

	public static final String EXCHANGE = "mail";
	public static final String QUEUE = "mail.send";
	public static final String ROUTING_KEY = "mail.send";
	public static final String DEAD_LETTER_EXCHANGE = "mail.dlx";
	public static final String DEAD_LETTER_QUEUE = "mail.send.dlq";

	@Bean
	DirectExchange mailExchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	DirectExchange mailDeadLetterExchange() {
		return new DirectExchange(DEAD_LETTER_EXCHANGE);
	}

	@Bean
	Queue sendQueue() {
		return QueueBuilder.durable(QUEUE)
				.deadLetterExchange(DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(DEAD_LETTER_QUEUE)
				.build();
	}

	@Bean
	Queue sendDeadLetterQueue() {
		return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding mailBinding() {
		return BindingBuilder.bind(sendQueue()).to(mailExchange()).with(ROUTING_KEY);
	}

	@Bean
	Binding mailDeadLetterBinding() {
		return BindingBuilder.bind(sendDeadLetterQueue()).to(mailDeadLetterExchange()).with(DEAD_LETTER_QUEUE);
	}

	@Bean
	MessageConverter messageConverter() {
		return new JacksonJsonMessageConverter();
	}

}
