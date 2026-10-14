package dev.salman.Lowcodebackend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	TokenService tokens;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	@MockitoBean
	JavaMailSender mail;

	/** The cookies a browser would hold after signing in. */
	record Browser(Cookie access, Cookie refresh) {
	}

	@BeforeEach
	void clean() {
		jdbc.update("delete from workspaces");
		jdbc.update("delete from users");
		redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
			c.serverCommands().flushAll();
			return null;
		});
	}

	User newUser(String email) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, email) values (?, ?)", id, email);
		return new User(id, email);
	}

	Browser signIn(User user) {
		Cookie access = null;
		Cookie refresh = null;
		for (ResponseCookie c : tokens.start(user)) {
			Cookie cookie = new Cookie(c.getName(), c.getValue());
			if (c.getName().equals("tessle_at")) {
				access = cookie;
			}
			else if (c.getName().equals("tessle_rt")) {
				refresh = cookie;
			}
		}
		return new Browser(access, refresh);
	}

	Cookie xsrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	MvcResult postWithCsrf(String path, Cookie... cookies) throws Exception {
		Cookie csrf = xsrf();
		var request = post(path).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
		for (Cookie c : cookies) {
			request = request.cookie(c);
		}
		return mvc.perform(request).andReturn();
	}

	int refresh(Cookie refreshToken) throws Exception {
		return postWithCsrf("/api/auth/refresh", refreshToken).getResponse().getStatus();
	}

	int meStatus(Cookie access) throws Exception {
		return mvc.perform(get("/api/auth/me").cookie(access)).andReturn().getResponse().getStatus();
	}

	static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void refreshRotatesTheTokenAndIssuesAWorkingSession() throws Exception {
		Browser first = signIn(newUser("rotate@example.com"));

		MvcResult result = postWithCsrf("/api/auth/refresh", first.refresh());

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		Cookie access = result.getResponse().getCookie("tessle_at");
		Cookie refresh = result.getResponse().getCookie("tessle_rt");
		assertThat(refresh.getValue()).isNotEqualTo(first.refresh().getValue());
		assertThat(meStatus(access)).isEqualTo(200);
		List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
		assertThat(setCookies.stream().filter(c -> c.startsWith("tessle_rt=")).findFirst().orElseThrow())
				.contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/api/auth/refresh");
	}

	@Test
	void refreshTokensAreStoredOnlyAsHashes() throws Exception {
		Browser browser = signIn(newUser("hash@example.com"));

		assertThat(redis.hasKey("rt:" + sha256(browser.refresh().getValue()))).isTrue();
		for (String key : redis.keys("*")) {
			assertThat(key).doesNotContain(browser.refresh().getValue());
		}
	}

	@Test
	void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
		Browser first = signIn(newUser("reuse@example.com"));
		MvcResult rotated = postWithCsrf("/api/auth/refresh", first.refresh());
		Cookie newAccess = rotated.getResponse().getCookie("tessle_at");
		Cookie newRefresh = rotated.getResponse().getCookie("tessle_rt");

		assertThat(refresh(first.refresh())).isEqualTo(401);

		assertThat(refresh(newRefresh)).as("legitimate holder is signed out too").isEqualTo(401);
		assertThat(meStatus(newAccess)).as("access tokens of the family die immediately").isEqualTo(401);
		assertThat(meStatus(first.access())).isEqualTo(401);
	}

	@Test
	void refreshWithoutOrWithGarbageTokenIsUnauthorized() throws Exception {
		assertThat(postWithCsrf("/api/auth/refresh").getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(new Cookie("tessle_rt", "garbage"))).isEqualTo(401);
	}

	@Test
	void anAccessTokenCannotBeUsedToRefreshNorTheOtherWayRound() throws Exception {
		Browser browser = signIn(newUser("confused@example.com"));

		assertThat(refresh(new Cookie("tessle_rt", browser.access().getValue()))).isEqualTo(401);
		assertThat(meStatus(new Cookie("tessle_at", browser.refresh().getValue()))).isEqualTo(401);
	}

	@Test
	void refreshRequiresTheCsrfToken() throws Exception {
		Browser browser = signIn(newUser("csrf@example.com"));

		mvc.perform(post("/api/auth/refresh").cookie(browser.refresh())).andExpect(status().isForbidden());
	}

	@Test
	void logoutEndsTheCurrentSessionAndClearsTheCookies() throws Exception {
		Browser browser = signIn(newUser("out@example.com"));

		MvcResult result = postWithCsrf("/api/auth/logout", browser.access());

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
		assertThat(setCookies.stream().filter(c -> c.startsWith("tessle_at=")).findFirst().orElseThrow())
				.contains("Max-Age=0").contains("Path=/;");
		assertThat(setCookies.stream().filter(c -> c.startsWith("tessle_rt=")).findFirst().orElseThrow())
				.contains("Max-Age=0").contains("Path=/api/auth/refresh");
		assertThat(meStatus(browser.access())).isEqualTo(401);
		assertThat(refresh(browser.refresh())).isEqualTo(401);
	}

	@Test
	void logoutDoesNotAffectOtherSessionsOfTheSameUser() throws Exception {
		User user = newUser("two@example.com");
		Browser laptop = signIn(user);
		Browser phone = signIn(user);

		postWithCsrf("/api/auth/logout", laptop.access());

		assertThat(meStatus(phone.access())).isEqualTo(200);
		assertThat(refresh(phone.refresh())).isEqualTo(204);
	}

	@Test
	void logoutEverywhereEndsAllSessionsOfThatUserOnly() throws Exception {
		User user = newUser("all@example.com");
		Browser laptop = signIn(user);
		Browser phone = signIn(user);
		Browser stranger = signIn(newUser("stranger@example.com"));

		MvcResult result = postWithCsrf("/api/auth/logout-all", laptop.access());

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(meStatus(laptop.access())).isEqualTo(401);
		assertThat(meStatus(phone.access())).isEqualTo(401);
		assertThat(refresh(phone.refresh())).isEqualTo(401);
		assertThat(meStatus(stranger.access())).isEqualTo(200);
		assertThat(refresh(stranger.refresh())).isEqualTo(204);
	}

	@Test
	void logoutRequiresASessionAndCsrf() throws Exception {
		Cookie csrf = xsrf();
		mvc.perform(post("/api/auth/logout").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isUnauthorized());
		Browser browser = signIn(newUser("nocsrf@example.com"));
		mvc.perform(post("/api/auth/logout").cookie(browser.access())).andExpect(status().isForbidden());
	}

	@Test
	void logoutEverywhereStillReachesAFamilyThatKeepsRotatingPastTheIndexLifetime() throws Exception {
		User user = newUser("long@example.com");
		Browser phone = signIn(user);
		redis.expire("user-fams:" + user.getId(), java.time.Duration.ofSeconds(5));

		MvcResult rotated = postWithCsrf("/api/auth/refresh", phone.refresh());
		assertThat(rotated.getResponse().getStatus()).isEqualTo(204);

		assertThat(redis.getExpire("user-fams:" + user.getId())).as("rotation keeps the index alive").isGreaterThan(86_400L);
		Cookie access = rotated.getResponse().getCookie("tessle_at");
		postWithCsrf("/api/auth/logout-all", access);
		assertThat(meStatus(access)).isEqualTo(401);
	}

	@Test
	void aReadableSessionHintCookieMarksSignedInBrowsersAndIsClearedOnLogout() throws Exception {
		Browser browser = signIn(newUser("hint@example.com"));
		ResponseCookie hint = java.util.Arrays.stream(tokens.start(newUser("hint2@example.com")))
				.filter(c -> c.getName().equals("tessle_s")).findFirst().orElseThrow();
		assertThat(hint.isHttpOnly()).isFalse();
		assertThat(hint.getValue()).isEqualTo("1");
		assertThat(hint.getPath()).isEqualTo("/");

		MvcResult out = postWithCsrf("/api/auth/logout", browser.access());
		assertThat(out.getResponse().getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("tessle_s=")).findFirst()
				.orElseThrow()).contains("Max-Age=0");
	}

	@Test
	void anEndedSessionCannotBeGivenAnActiveWorkspace() throws Exception {
		Browser browser = signIn(newUser("ghost@example.com"));
		var jwt = jwtDecoder.decode(browser.access().getValue());
		postWithCsrf("/api/auth/logout", browser.access());

		assertThat(tokens.activateWorkspace(jwt, UUID.randomUUID())).isEmpty();
		assertThat(redis.hasKey("famws:" + jwt.getClaimAsString("sid"))).isFalse();
	}

	@Test
	void revokingAFamilyForgetsItsActiveWorkspaceToo() throws Exception {
		Browser browser = signIn(newUser("tidy@example.com"));
		var jwt = jwtDecoder.decode(browser.access().getValue());
		String key = "famws:" + jwt.getClaimAsString("sid");
		assertThat(tokens.activateWorkspace(jwt, UUID.randomUUID())).isPresent();
		assertThat(redis.hasKey(key)).isTrue();

		postWithCsrf("/api/auth/refresh", browser.refresh());
		refresh(browser.refresh());

		assertThat(redis.hasKey(key)).as("reuse detection clears it").isFalse();
	}

}
