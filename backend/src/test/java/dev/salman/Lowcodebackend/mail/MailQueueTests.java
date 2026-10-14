package dev.salman.Lowcodebackend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"spring.rabbitmq.listener.simple.retry.max-retries=2",
		"spring.rabbitmq.listener.simple.retry.initial-interval=20ms",
		"spring.rabbitmq.listener.simple.retry.multiplier=1.0",
		"spring.rabbitmq.listener.simple.retry.max-interval=50ms"
})
class MailQueueTests {

	@Autowired
	MailQueue mailQueue;

	@Autowired
	RabbitTemplate rabbit;

	@MockitoBean
	JavaMailSender sender;

	@AfterEach
	void clean() {
		MDC.clear();
		rabbit.execute(channel -> channel.queuePurge(MailConfig.QUEUE));
		rabbit.execute(channel -> channel.queuePurge(MailConfig.DEAD_LETTER_QUEUE));
	}

	@Test
	void enqueuedMailIsDelivered() {
		mailQueue.enqueue(MailMessage.of("owner@example.com", "Hello", "Body text"));

		var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender, timeout(5000)).send(captor.capture());
		assertThat(captor.getValue().getTo()).containsExactly("owner@example.com");
		assertThat(captor.getValue().getSubject()).isEqualTo("Hello");
		assertThat(captor.getValue().getText()).isEqualTo("Body text");
	}

	@Test
	void transientFailuresAreRetriedUntilDelivered() {
		reset(sender);
		doThrow(new MailSendException("smtp down")).doThrow(new MailSendException("smtp down"))
				.doNothing().when(sender).send(any(SimpleMailMessage.class));

		mailQueue.enqueue(MailMessage.of("a@example.com", "Retry", "x"));

		verify(sender, timeout(5000).times(3)).send(any(SimpleMailMessage.class));
		assertThat(rabbit.receive(MailConfig.DEAD_LETTER_QUEUE, 300)).isNull();
	}

	@Test
	void permanentFailureLandsInDeadLetterQueueAfterTheRetryLimit() {
		reset(sender);
		doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));

		mailQueue.enqueue(MailMessage.of("b@example.com", "Doomed", "x"));

		Message dead = rabbit.receive(MailConfig.DEAD_LETTER_QUEUE, 5000);
		assertThat(dead).isNotNull();
		verify(sender, times(3)).send(any(SimpleMailMessage.class));
	}

	@Test
	void redeliveringTheSameMessageSendsOnlyOneMail() {
		reset(sender);
		doNothing().when(sender).send(any(SimpleMailMessage.class));
		MailMessage message = MailMessage.of("c@example.com", "Once", "x");

		mailQueue.enqueue(message);
		verify(sender, timeout(5000)).send(any(SimpleMailMessage.class));
		mailQueue.enqueue(message);

		await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3))
				.untilAsserted(() -> verify(sender, times(1)).send(any(SimpleMailMessage.class)));
	}

	@Test
	void traceIdOfTheEnqueuingRequestIsVisibleWhileTheConsumerSends() {
		reset(sender);
		AtomicReference<String> seen = new AtomicReference<>();
		doAnswer(invocation -> {
			seen.set(MDC.get("traceId"));
			return null;
		}).when(sender).send(any(SimpleMailMessage.class));

		MDC.put("traceId", "trace-123");
		mailQueue.enqueue(MailMessage.of("d@example.com", "Trace", "x"));

		verify(sender, timeout(5000).atLeast(1)).send(any(SimpleMailMessage.class));
		await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(seen.get()).isEqualTo("trace-123"));
	}

	@Test
	void invalidMessageIsDeadLetteredAndNeverSentOrMarkedAsSent() {
		reset(sender);

		mailQueue.enqueue(new MailMessage(" ", "e@example.com", "No id", "x"));
		mailQueue.enqueue(new MailMessage("some-id", " ", "No recipient", "x"));

		assertThat(rabbit.receive(MailConfig.DEAD_LETTER_QUEUE, 5000)).isNotNull();
		assertThat(rabbit.receive(MailConfig.DEAD_LETTER_QUEUE, 5000)).isNotNull();
		verify(sender, times(0)).send(any(SimpleMailMessage.class));
	}

}
