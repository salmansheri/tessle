package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

/** Data sources through the HTTP API, against the real platform stack and a real tenant PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestSessions.class, TestNetwork.class })
class DataSourcesApiTests {

	static final String SECRET = "s3cret-Pw-9f2c1";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestSessions sessions;

	@Autowired
	JdbcTemplate admin;

	@MockitoBean
	JavaMailSender mail;

	final ObjectMapper json = new ObjectMapper();

	@BeforeEach
	void clean() {
		admin.update("delete from audit_events");
		admin.update("delete from workspaces");
		admin.update("delete from users");
		sessions.flushRedis();
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

	record Owner(Cookie access, UUID workspaceId) {
	}

	Owner ownerOfNewWorkspace(String email, String name) throws Exception {
		TestSessions.Browser browser = sessions.signIn(email);
		MvcResult created = send(post("/api/workspaces"), browser.access(), "{\"name\":\"" + name + "\"}");
		return new Owner(created.getResponse().getCookie("tessle_at"), UUID.fromString(body(created).get("id").asString()));
	}

	/** Makes a new person a member with the given role (through the privileged connection) and has them act in it. */
	Cookie memberWithRole(UUID workspace, String email, String role) throws Exception {
		TestSessions.Browser browser = sessions.signIn(email);
		admin.update("insert into memberships (workspace_id, user_id, role) values (?, ?, ?)", workspace, browser.userId(), role);
		MvcResult activated = send(post("/api/workspaces/" + workspace + "/activate"), browser.access(), null);
		assertThat(activated.getResponse().getStatus()).isEqualTo(204);
		return activated.getResponse().getCookie("tessle_at");
	}

	/** A request body for a Data source pointing at the seeded tenant PostgreSQL, with the given password. */
	String sourceJson(String logicalName, String password) {
		TenantDatabase db = TenantDatabases.postgres();
		// The tenant server uses a self-signed certificate, so the data source trusts it as its CA.
		return """
				{"logicalName":"%s","label":"Northwind","type":"postgresql","host":"%s","port":%d,
				 "database":"tenant","username":"%s","password":"%s","caCertificate":%s}
				""".formatted(logicalName, db.host(), db.port(), db.username(), password, json.writeValueAsString(db.caCertificatePem()));
	}

	@Test
	void ownerCreatesADataSourceAndItAppearsInTheListWithoutAnySecret() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		MvcResult created = send(post("/api/data-sources"), ada.access(), sourceJson("northwind", SECRET));
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		assertThat(body(created).get("logicalName").asString()).isEqualTo("northwind");
		assertThat(body(created).get("state").asString()).isEqualTo("disabled");

		MvcResult list = mvc.perform(get("/api/data-sources").cookie(ada.access())).andReturn();
		assertThat(list.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(list)).hasSize(1);
		assertThat(body(list).get(0).get("label").asString()).isEqualTo("Northwind");

		// Write-only: the password appears in no response, however it is asked for.
		assertThat(created.getResponse().getContentAsString()).doesNotContain(SECRET);
		assertThat(list.getResponse().getContentAsString()).doesNotContain(SECRET);
	}

	@Test
	void ownersAndAdminsManageBuildersOnlyListAndEditorsAndViewersAreRefused() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Cookie admin = memberWithRole(ada.workspaceId(), "admin@example.com", "admin");
		Cookie builder = memberWithRole(ada.workspaceId(), "builder@example.com", "builder");
		Cookie editor = memberWithRole(ada.workspaceId(), "editor@example.com", "editor");
		Cookie viewer = memberWithRole(ada.workspaceId(), "viewer@example.com", "viewer");

		assertThat(send(post("/api/data-sources"), admin, sourceJson("by_admin", SECRET)).getResponse().getStatus()).isEqualTo(201);
		assertThat(send(post("/api/data-sources"), builder, sourceJson("by_builder", SECRET)).getResponse().getStatus()).isEqualTo(403);
		assertThat(send(post("/api/data-sources"), editor, sourceJson("by_editor", SECRET)).getResponse().getStatus()).isEqualTo(403);
		assertThat(send(post("/api/data-sources"), viewer, sourceJson("by_viewer", SECRET)).getResponse().getStatus()).isEqualTo(403);

		// A builder sees that the Data source exists, never how it is reached.
		MvcResult asBuilder = mvc.perform(get("/api/data-sources").cookie(builder)).andReturn();
		assertThat(asBuilder.getResponse().getStatus()).isEqualTo(200);
		JsonNode first = body(asBuilder).get(0);
		assertThat(first.get("label").asString()).isEqualTo("Northwind");
		assertThat(first.hasNonNull("host")).isFalse();
		assertThat(first.hasNonNull("username")).isFalse();
		assertThat(mvc.perform(get("/api/data-sources").cookie(editor)).andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(mvc.perform(get("/api/data-sources").cookie(viewer)).andReturn().getResponse().getStatus()).isEqualTo(403);

		MvcResult asAdmin = mvc.perform(get("/api/data-sources").cookie(admin)).andReturn();
		assertThat(body(asAdmin).get(0).get("host").asString()).isEqualTo(TenantDatabases.postgres().host());
	}

	@Test
	void logicalNameIsUniquePerWorkspaceButReusableInAnother() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Owner bob = ownerOfNewWorkspace("bob@example.com", "Globex");

		assertThat(send(post("/api/data-sources"), ada.access(), sourceJson("sales", SECRET)).getResponse().getStatus()).isEqualTo(201);
		assertThat(send(post("/api/data-sources"), ada.access(), sourceJson("sales", SECRET)).getResponse().getStatus()).isEqualTo(409);
		assertThat(send(post("/api/data-sources"), bob.access(), sourceJson("sales", SECRET)).getResponse().getStatus()).isEqualTo(201);

		// Another Workspace's Data sources are invisible.
		assertThat(body(mvc.perform(get("/api/data-sources").cookie(bob.access())).andReturn())).hasSize(1);
	}

	@Test
	void connectionFieldsAreStructuredAndAnythingElseIsRefused() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		String ok = sourceJson("sales", SECRET);

		// A host cannot smuggle a URL, a port or driver options; unknown properties are not accepted either.
		for (String host : new String[] { "db.example.com/x?ssl=false", "jdbc:postgresql://evil", "a b", "host:5432", "" }) {
			String bad = ok.replaceFirst("\"host\":\"[^\"]*\"", "\"host\":\"" + host + "\"");
			assertThat(send(post("/api/data-sources"), ada.access(), bad).getResponse().getStatus()).as(host).isEqualTo(400);
		}
		String withOptions = ok.replace("\"database\"", "\"jdbcUrl\":\"jdbc:postgresql://x/y\",\"options\":\"a=b\",\"database\"");
		assertThat(send(post("/api/data-sources"), ada.access(), withOptions).getResponse().getStatus()).isEqualTo(400);
		assertThat(send(post("/api/data-sources"), ada.access(), ok.replace("\"sales\"", "\"Not Valid\"")).getResponse().getStatus()).isEqualTo(400);
		assertThat(send(post("/api/data-sources"), ada.access(), ok.replace("\"postgresql\"", "\"mongodb\"")).getResponse().getStatus()).isEqualTo(400);
		assertThat(send(post("/api/data-sources"), ada.access(), ok.replace("\"port\":" + TenantDatabases.postgres().port(), "\"port\":70000")).getResponse().getStatus()).isEqualTo(400);
		assertThat(body(mvc.perform(get("/api/data-sources").cookie(ada.access())).andReturn())).isEmpty();
	}

	@Test
	void thePasswordIsEncryptedAtRestAndCreationIsAuditedWithoutSecrets() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		MvcResult created = send(post("/api/data-sources"), ada.access(), sourceJson("sales", SECRET));
		UUID id = UUID.fromString(body(created).get("id").asString());

		byte[] stored = admin.queryForObject("select password_ciphertext from data_sources where id = ?", byte[].class, id);
		assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
		assertThat(admin.queryForObject("select secret_key_id from data_sources where id = ?", String.class, id)).isEqualTo("k1");

		var events = admin.queryForList("select actor_id, action, target_id, detail::text as detail from audit_events");
		assertThat(events).hasSize(1);
		assertThat(events.get(0).get("action")).isEqualTo("datasource.created");
		assertThat(events.get(0).get("target_id")).isEqualTo(id);
		assertThat((String) events.get(0).get("detail")).contains("sales").doesNotContain(SECRET);
	}

	/** Creates a Data source for the seeded tenant database with the given overrides and returns its id. */
	UUID create(Cookie access, String logicalName, String password, String database, Integer port) throws Exception {
		TenantDatabase db = TenantDatabases.postgres();
		String json = sourceJson(logicalName, password).replace("\"database\":\"tenant\"", "\"database\":\"" + database + "\"");
		if (port != null) {
			json = json.replace("\"port\":" + db.port(), "\"port\":" + port);
		}
		MvcResult created = send(post("/api/data-sources"), access, json);
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		return UUID.fromString(body(created).get("id").asString());
	}

	JsonNode test(Cookie access, UUID id) throws Exception {
		MvcResult r = send(post("/api/data-sources/" + id + "/test"), access, null);
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		return body(r);
	}

	@Test
	void aConnectionTestReportsSuccessOrADistinctError() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		String realPassword = TenantDatabases.postgres().password();

		JsonNode ok = test(ada.access(), create(ada.access(), "good", realPassword, "tenant", null));
		assertThat(ok.get("ok").asBoolean()).isTrue();

		JsonNode badLogin = test(ada.access(), create(ada.access(), "badlogin", "wrong-password", "tenant", null));
		assertThat(badLogin.get("ok").asBoolean()).isFalse();
		assertThat(badLogin.get("error").asString()).isEqualTo("bad_credentials");

		JsonNode noDb = test(ada.access(), create(ada.access(), "nodb", realPassword, "no_such_database", null));
		assertThat(noDb.get("error").asString()).isEqualTo("unknown_database");

		JsonNode down = test(ada.access(), create(ada.access(), "down", realPassword, "tenant", 1));
		assertThat(down.get("error").asString()).isEqualTo("unreachable");

		// The latest result is part of the listing, and no response ever echoes a password.
		MvcResult list = mvc.perform(get("/api/data-sources").cookie(ada.access())).andReturn();
		assertThat(list.getResponse().getContentAsString()).doesNotContain(realPassword).doesNotContain("wrong-password");
		assertThat(body(list).findValuesAsString("lastTestAt")).isNotEmpty();
	}

	@Test
	void testingIsAuditedWithOutcomeButNeverASecret() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		UUID id = create(ada.access(), "good", TenantDatabases.postgres().password(), "tenant", null);
		test(ada.access(), id);

		var detail = admin.queryForObject("select detail::text from audit_events where action = 'datasource.tested'", String.class);
		assertThat(detail).contains("\"ok\": true").doesNotContain(TenantDatabases.postgres().password() + "\"");
	}

	@Test
	void aDataSourceCanBeEnabledOnlyAfterAPassingTest() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Cookie builder = memberWithRole(ada.workspaceId(), "builder@example.com", "builder");
		UUID id = create(ada.access(), "good", TenantDatabases.postgres().password(), "tenant", null);

		assertThat(send(post("/api/data-sources/" + id + "/enable"), ada.access(), null).getResponse().getStatus()).isEqualTo(409);

		test(ada.access(), id);
		assertThat(send(post("/api/data-sources/" + id + "/enable"), builder, null).getResponse().getStatus()).isEqualTo(403);
		MvcResult enabled = send(post("/api/data-sources/" + id + "/enable"), ada.access(), null);
		assertThat(enabled.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(enabled).get("state").asString()).isEqualTo("enabled");

		// A failing test never lets a broken source be enabled.
		UUID broken = create(ada.access(), "broken", "wrong-password", "tenant", null);
		test(ada.access(), broken);
		assertThat(send(post("/api/data-sources/" + broken + "/enable"), ada.access(), null).getResponse().getStatus()).isEqualTo(409);

		assertThat(send(post("/api/data-sources/" + UUID.randomUUID() + "/test"), ada.access(), null).getResponse().getStatus()).isEqualTo(404);
		assertThat(send(post("/api/data-sources/" + id + "/test"), builder, null).getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void editingTheLabelKeepsTheTestButChangingHowItConnectsInvalidatesIt() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Cookie builder = memberWithRole(ada.workspaceId(), "builder@example.com", "builder");
		UUID id = create(ada.access(), "sales", TenantDatabases.postgres().password(), "tenant", null);
		test(ada.access(), id);
		send(post("/api/data-sources/" + id + "/enable"), ada.access(), null);

		MvcResult relabelled = send(patch("/api/data-sources/" + id), ada.access(), "{\"label\":\"Sales warehouse\"}");
		assertThat(relabelled.getResponse().getStatus()).isEqualTo(200);
		assertThat(body(relabelled).get("label").asString()).isEqualTo("Sales warehouse");
		assertThat(body(relabelled).get("state").asString()).isEqualTo("enabled");
		assertThat(body(relabelled).get("logicalName").asString()).isEqualTo("sales");

		assertThat(send(patch("/api/data-sources/" + id), ada.access(), "{\"label\":\"   \"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(send(patch("/api/data-sources/" + id), ada.access(), "{\"host\":\"\"}").getResponse().getStatus()).isEqualTo(400);

		// The logical name is immutable, and builders cannot edit.
		assertThat(send(patch("/api/data-sources/" + id), ada.access(), "{\"logicalName\":\"renamed\"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(send(patch("/api/data-sources/" + id), builder, "{\"label\":\"x\"}").getResponse().getStatus()).isEqualTo(403);

		// A new password changes how it connects: the earlier test no longer counts and the source is switched off.
		MvcResult rotated = send(patch("/api/data-sources/" + id), ada.access(), "{\"password\":\"" + SECRET + "\"}");
		assertThat(body(rotated).get("state").asString()).isEqualTo("disabled");
		assertThat(body(rotated).get("health").get("lastTestOk").isNull()).isTrue();
		assertThat(rotated.getResponse().getContentAsString()).doesNotContain(SECRET);
		assertThat(test(ada.access(), id).get("error").asString()).isEqualTo("bad_credentials");

		var events = admin.queryForList("select detail::text as d from audit_events where action = 'datasource.updated' order by created_at");
		assertThat(events).hasSize(2);
		assertThat((String) events.get(0).get("d")).contains("label");
		assertThat((String) events.get(1).get("d")).contains("password").doesNotContain(SECRET);
	}

}
