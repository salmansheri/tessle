package dev.salman.Lowcodebackend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbit() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redis() {
		return new GenericContainer<>(DockerImageName.parse("redis:8")).withExposedPorts(6379);
	}

	/** Secrets the app refuses to start without. Test-only values. */
	@Bean
	DynamicPropertyRegistrar testSecrets() {
		return registry -> {
			registry.add("tessle.jwt.secret", () -> "test-only-jwt-secret-at-least-32-bytes-long!!");
			registry.add("tessle.otp.secret", () -> "test-only-otp-secret-at-least-32-bytes-long!!");
			// 32 zero bytes, base64: a throwaway AES-256 key for tests only.
			registry.add("tessle.secrets.master-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
		};
	}

}
