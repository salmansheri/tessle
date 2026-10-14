package dev.salman.Lowcodebackend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * MockMvc skips the servlet container's error dispatch to /error, which Spring Security also guards.
 * Over a real server a blocked /error turns every error status into 401, so these go over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RealServerErrorStatusTests {

	@Value("${local.server.port}")
	int port;

	HttpResponse<String> post(String path, String json) throws Exception {
		return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void missingCsrfTokenIsForbiddenNotUnauthorized() throws Exception {
		assertThat(post("/api/auth/otp/request", "{\"email\":\"a@example.com\"}").statusCode()).isEqualTo(403);
	}

	@Test
	void unknownPathsStillRequireAuthentication() throws Exception {
		HttpResponse<String> r = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/nope")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(r.statusCode()).isEqualTo(401);
	}

}
