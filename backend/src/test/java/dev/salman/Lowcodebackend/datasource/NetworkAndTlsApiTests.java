package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import dev.salman.Lowcodebackend.identity.TestSessions;
import dev.salman.Lowcodebackend.tenantdb.TenantDatabase;
import dev.salman.Lowcodebackend.tenantdb.TenantDatabases;
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

/** TLS rules for Data source connections, against a real TLS-enabled PostgreSQL (loopback relaxed by TestNetwork). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestSessions.class, TestNetwork.class })
class NetworkAndTlsApiTests {

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
		MvcResult created = send(post("/api/workspaces"), browser.access(), "{\"name\":\"Acme\"}");
		owner = created.getResponse().getCookie("tessle_at");
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

	JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	/** Creates a Data source for a tenant database; null extras are left out of the request. */
	MvcResult create(TenantDatabase db, String host, String ca, Boolean verify) throws Exception {
		StringBuilder b = new StringBuilder("{\"logicalName\":\"src_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
				+ "\",\"label\":\"Tenant\",\"type\":\"postgresql\",\"host\":\"" + host + "\",\"port\":" + db.port()
				+ ",\"database\":\"tenant\",\"username\":\"" + db.username() + "\",\"password\":\"" + db.password() + "\"");
		if (ca != null) {
			b.append(",\"caCertificate\":").append(json.writeValueAsString(ca));
		}
		if (verify != null) {
			b.append(",\"verifyCertificate\":").append(verify);
		}
		return send(post("/api/data-sources"), owner, b.append("}").toString());
	}

	String testOutcome(MvcResult created) throws Exception {
		UUID id = UUID.fromString(body(created).get("id").asString());
		JsonNode r = body(send(post("/api/data-sources/" + id + "/test"), owner, null));
		return r.get("ok").asBoolean() ? "ok" : r.get("error").asString();
	}

	@Test
	void aServerWithAnUntrustedCertificateIsRefusedUntilItsCaIsUploaded() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();

		assertThat(testOutcome(create(db, db.host(), null, null))).isEqualTo("tls_failed");
		assertThat(testOutcome(create(db, db.host(), db.caCertificatePem(), null))).isEqualTo("ok");
	}

	@Test
	void theCaCertificateIsStoredEncryptedAndNeverReturned() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();
		MvcResult created = create(db, db.host(), db.caCertificatePem(), null);
		UUID id = UUID.fromString(body(created).get("id").asString());

		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		assertThat(body(created).get("hasCaCertificate").asBoolean()).isTrue();
		assertThat(body(created).get("tlsVerify").asBoolean()).isTrue();
		assertThat(created.getResponse().getContentAsString()).doesNotContain("BEGIN CERTIFICATE");
		byte[] stored = admin.queryForObject("select ca_ciphertext from data_sources where id = ?", byte[].class, id);
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("BEGIN CERTIFICATE");
		assertThat(admin.queryForObject("select ca_key_id from data_sources where id = ?", String.class, id)).isEqualTo("k1");
	}

	@Test
	void somethingThatIsNotACertificateIsRefused() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();
		assertThat(create(db, db.host(), "not a certificate", null).getResponse().getStatus()).isEqualTo(400);
		assertThat(create(db, db.host(), "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----", null).getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	void theConnectionGoesToTheAddressResolvedOnceAndTheCertificateIsCheckedAgainstTheNameGiven() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();

		// The JVM cannot resolve this name at all; only the policy's one lookup knows it, so a connection proves pinning.
		assertThat(testOutcome(create(db, TestNetwork.RIGHT_NAME, db.caCertificatePem(), null))).isEqualTo("ok");
		// Same address, but the certificate does not name it: hostname verification must still fail.
		assertThat(testOutcome(create(db, TestNetwork.WRONG_NAME, db.caCertificatePem(), null))).isEqualTo("tls_failed");
	}

	@Test
	void turningVerificationOffIsExplicitAllowsAnUntrustedServerAndIsAudited() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();
		MvcResult created = create(db, db.host(), null, false);

		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		assertThat(body(created).get("tlsVerify").asBoolean()).isFalse();
		assertThat(testOutcome(created)).isEqualTo("ok");
		UUID id = UUID.fromString(body(created).get("id").asString());
		var events = admin.queryForList("select actor_id, target_id from audit_events where action = 'datasource.tls_verification_disabled'");
		assertThat(events).hasSize(1);
		assertThat(events.get(0).get("target_id")).isEqualTo(id);

		// Turning it back on later is also a recorded change, and the old test no longer counts.
		MvcResult on = send(patch("/api/data-sources/" + id), owner, "{\"verifyCertificate\":true}");
		assertThat(body(on).get("tlsVerify").asBoolean()).isTrue();
		assertThat(body(on).get("health").get("lastTestOk").isNull()).isTrue();
		assertThat(admin.queryForObject("select count(*) from audit_events where action = 'datasource.updated' and detail::text like '%verifyCertificate%'", Integer.class)).isEqualTo(1);
	}

	@Test
	void editingTheVerificationSettingToOffIsAuditedToo() throws Exception {
		TenantDatabase db = TenantDatabases.postgres();
		UUID id = UUID.fromString(body(create(db, db.host(), db.caCertificatePem(), null)).get("id").asString());

		send(patch("/api/data-sources/" + id), owner, "{\"verifyCertificate\":false}");

		assertThat(admin.queryForObject("select count(*) from audit_events where action = 'datasource.tls_verification_disabled' and target_id = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void aServerWithoutTlsIsRefusedEvenWhenVerificationIsOff() throws Exception {
		TenantDatabase plain = TenantDatabases.postgresWithoutTls();

		assertThat(testOutcome(create(plain, plain.host(), null, null))).isEqualTo("tls_failed");
		assertThat(testOutcome(create(plain, plain.host(), null, false))).isEqualTo("tls_failed");
	}

}
