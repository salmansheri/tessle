package dev.salman.Lowcodebackend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OtpSignInTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@MockitoBean
	JavaMailSender mail;

	/** CSRF double-submit state, as a browser would hold it. */
	record Csrf(Cookie cookie, String token) {
	}

	@BeforeEach
	void clean() {
		reset(mail);
		jdbc.update("delete from otp_codes");
		jdbc.update("delete from users");
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
			c.serverCommands().flushAll();
			return null;
		});
	}

	Csrf csrf() throws Exception {
		MvcResult r = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		Cookie cookie = r.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		return new Csrf(cookie, cookie.getValue());
	}

	int requestCode(String email) throws Exception {
		Csrf csrf = csrf();
		return mvc.perform(post("/api/auth/otp/request").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
				.andReturn().getResponse().getStatus();
	}

	/** The most recent code mailed to {@code email}, read from what the consumer handed to the mail sender. */
	String lastCodeFor(String email) {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(mail, atLeastOnce()).send(captor.capture()));
		List<SimpleMailMessage> sent = captor.getAllValues().stream()
				.filter(m -> m.getTo() != null && m.getTo().length == 1 && m.getTo()[0].equals(email)).toList();
		assertThat(sent).isNotEmpty();
		Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.get(sent.size() - 1).getText());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	MvcResult submitCode(String email, String code) throws Exception {
		Csrf csrf = csrf();
		return mvc.perform(post("/api/auth/otp/verify").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")).andReturn();
	}

	@Test
	void fullSignInFlowSetsCookiesAndWhoAmIWorks() throws Exception {
		assertThat(requestCode("Ada@Example.com")).isEqualTo(202);
		String code = lastCodeFor("ada@example.com");

		MvcResult result = submitCode("ada@example.com", code);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
		String access = setCookies.stream().filter(c -> c.startsWith("tessle_at=")).findFirst().orElseThrow();
		String refresh = setCookies.stream().filter(c -> c.startsWith("tessle_rt=")).findFirst().orElseThrow();
		assertThat(access).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/;");
		assertThat(access).contains("Max-Age=600");
		assertThat(refresh).contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
				.contains("Path=/api/auth/refresh").contains("Max-Age=604800");

		mvc.perform(get("/api/auth/me").cookie(result.getResponse().getCookie("tessle_at")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ada@example.com"))
				.andExpect(jsonPath("$.id").isNotEmpty());
	}

	@Test
	void whoAmIWithoutASessionIsUnauthorized() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void firstSignInCreatesTheUserAndLaterSignInsReuseIt() throws Exception {
		requestCode("grace@example.com");
		assertThat(submitCode("grace@example.com", lastCodeFor("grace@example.com")).getResponse().getStatus()).isEqualTo(200);
		reset(mail);
		requestCode("grace@example.com");
		assertThat(submitCode("grace@example.com", lastCodeFor("grace@example.com")).getResponse().getStatus()).isEqualTo(200);

		assertThat(jdbc.queryForObject("select count(*) from users where email = 'grace@example.com'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void unknownAndKnownEmailsGetIdenticalResponses() throws Exception {
		requestCode("known@example.com");
		submitCode("known@example.com", lastCodeFor("known@example.com"));

		int knownStatus = requestCode("known@example.com");
		int unknownStatus = requestCode("nobody@example.com");
		assertThat(knownStatus).isEqualTo(202).isEqualTo(unknownStatus);

		MvcResult wrongKnown = submitCode("known@example.com", "000000");
		MvcResult wrongUnknown = submitCode("never-requested@example.com", "000000");
		assertThat(wrongKnown.getResponse().getStatus()).isEqualTo(401);
		assertThat(wrongKnown.getResponse().getStatus()).isEqualTo(wrongUnknown.getResponse().getStatus());
		assertThat(wrongKnown.getResponse().getContentAsString()).isEqualTo(wrongUnknown.getResponse().getContentAsString());
	}

	@Test
	void aNewCodeInvalidatesThePreviousOne() throws Exception {
		requestCode("new@example.com");
		String first = lastCodeFor("new@example.com");
		reset(mail);
		requestCode("new@example.com");
		String second = lastCodeFor("new@example.com");
		assertThat(second).isNotEqualTo(first);

		assertThat(submitCode("new@example.com", first).getResponse().getStatus()).isEqualTo(401);
		assertThat(submitCode("new@example.com", second).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void aCodeIsLockedAfterFiveWrongAttempts() throws Exception {
		requestCode("lock@example.com");
		String code = lastCodeFor("lock@example.com");
		String wrong = code.equals("111111") ? "222222" : "111111";

		for (int i = 0; i < 5; i++) {
			assertThat(submitCode("lock@example.com", wrong).getResponse().getStatus()).isEqualTo(401);
		}
		assertThat(submitCode("lock@example.com", code).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void anExpiredCodeIsRejected() throws Exception {
		requestCode("late@example.com");
		String code = lastCodeFor("late@example.com");
		jdbc.update("update otp_codes set expires_at = now() - interval '1 second' where email = 'late@example.com'");

		assertThat(submitCode("late@example.com", code).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void aCodeCannotBeUsedTwice() throws Exception {
		requestCode("once@example.com");
		String code = lastCodeFor("once@example.com");

		assertThat(submitCode("once@example.com", code).getResponse().getStatus()).isEqualTo(200);
		assertThat(submitCode("once@example.com", code).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void codesAreStoredHashedWithTenMinuteExpiry() throws Exception {
		requestCode("hash@example.com");
		String code = lastCodeFor("hash@example.com");

		String stored = jdbc.queryForObject("select code_hash from otp_codes where email = 'hash@example.com'", String.class);
		assertThat(stored).isNotEqualTo(code).doesNotContain(code);
		Long seconds = jdbc.queryForObject(
				"select extract(epoch from (expires_at - created_at))::bigint from otp_codes where email = 'hash@example.com'",
				Long.class);
		assertThat(seconds).isBetween(595L, 605L);
	}

	@Test
	void requestingCodesIsRateLimitedPerEmail() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(requestCode("spam@example.com")).isEqualTo(202);
		}
		assertThat(requestCode("spam@example.com")).isEqualTo(429);
		assertThat(requestCode("other@example.com")).isEqualTo(202);
	}

	@Test
	void stateChangingRequestsWithoutTheCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@example.com\"}")).andExpect(status().isForbidden());

		Csrf csrf = csrf();
		mvc.perform(post("/api/auth/otp/request").cookie(csrf.cookie()).header("X-XSRF-TOKEN", "wrong")
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void malformedEmailIsABadRequest() throws Exception {
		assertThat(requestCode("not-an-email")).isEqualTo(400);
	}

	@Test
	void aStaleAccessCookieDoesNotBlockSigningInAgain() throws Exception {
		Cookie stale = new Cookie("tessle_at", "not-a-valid-jwt");

		MvcResult r = mvc.perform(get("/api/auth/csrf").cookie(stale)).andExpect(status().isOk()).andReturn();
		Cookie csrfCookie = r.getResponse().getCookie("XSRF-TOKEN");
		String token = csrfCookie.getValue();

		mvc.perform(post("/api/auth/otp/request").cookie(stale, csrfCookie).header("X-XSRF-TOKEN", token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"stale@example.com\"}"))
				.andExpect(status().isAccepted());
		mvc.perform(get("/api/auth/me").cookie(stale)).andExpect(status().isUnauthorized());
	}

}
