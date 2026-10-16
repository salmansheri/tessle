package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import dev.salman.Lowcodebackend.identity.TestSessions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The real network policy end to end: no test override, so loopback and private addresses are refused. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestSessions.class })
class NetworkPolicyApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestSessions sessions;

	@Autowired
	JdbcTemplate admin;

	@MockitoBean
	JavaMailSender mail;

	final ObjectMapper json = new ObjectMapper();
	Cookie owner;

	@BeforeEach
	void signIn() throws Exception {
		admin.update("delete from audit_events");
		admin.update("delete from workspaces");
		admin.update("delete from users");
		sessions.flushRedis();
		TestSessions.Browser browser = sessions.signIn("ada@example.com");
		owner = send(post("/api/workspaces"), browser.access(), "{\"name\":\"Acme\"}").getResponse().getCookie("tessle_at");
	}

	MvcResult send(MockHttpServletRequestBuilder request, Cookie access, String body) throws Exception {
		Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		request = request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
		if (access != null) {
			request = request.cookie(access);
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request).andReturn();
	}

	UUID create(String host) throws Exception {
		String json = """
				{"logicalName":"src_%s","label":"Target","type":"postgresql","host":"%s","port":5432,
				 "database":"d","username":"u","password":"p"}
				""".formatted(UUID.randomUUID().toString().replace("-", "").substring(0, 8), host);
		MvcResult created = send(post("/api/data-sources"), owner, json);
		assertThat(created.getResponse().getStatus()).as(host).isEqualTo(201);
		return UUID.fromString(this.json.readTree(created.getResponse().getContentAsString()).get("id").asString());
	}

	JsonNode test(UUID id) throws Exception {
		MvcResult r = send(post("/api/data-sources/" + id + "/test"), owner, null);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return json.readTree(r.getResponse().getContentAsString());
	}

	@Test
	void testsAgainstLoopbackLinkLocalMetadataAndPrivateAddressesAreRefused() throws Exception {
		// "localhost" is a name that resolves to loopback, so the check follows the answer, not the spelling.
		for (String host : new String[] { "127.0.0.1", "localhost", "169.254.169.254", "10.0.0.5", "192.168.1.20" }) {
			JsonNode result = test(create(host));
			assertThat(result.get("ok").asBoolean()).as(host).isFalse();
			assertThat(result.get("error").asString()).as(host).isEqualTo("blocked_address");
		}
	}

	@Test
	void enablingRechecksTheAddressEvenIfAnEarlierTestPassed() throws Exception {
		UUID id = create("127.0.0.1");
		// Pretend the source passed a test back when the address was allowed.
		admin.update("update data_sources set last_test_ok = true, last_test_at = now() where id = ?", id);

		MvcResult enable = send(post("/api/data-sources/" + id + "/enable"), owner, null);

		assertThat(enable.getResponse().getStatus()).isEqualTo(409);
		assertThat(admin.queryForObject("select state from data_sources where id = ?", String.class, id)).isEqualTo("disabled");
	}

	@Test
	void anUnresolvableNameIsReportedAsUnreachable() throws Exception {
		assertThat(test(create("does-not-exist.invalid")).get("error").asString()).isEqualTo("unreachable");
	}

}
