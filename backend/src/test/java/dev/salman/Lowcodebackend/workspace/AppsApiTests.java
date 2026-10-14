package dev.salman.Lowcodebackend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestSessions.class })
class AppsApiTests {

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

	/** Signs in as a new person who creates a workspace; returns {access cookie acting in it, workspace id}. */
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

	int createApp(Cookie access, String name) throws Exception {
		return send(post("/api/apps"), access, "{\"name\":\"" + name + "\"}").getResponse().getStatus();
	}

	List<String> appNames(Cookie access) throws Exception {
		MvcResult r = mvc.perform(get("/api/apps").cookie(access)).andReturn();
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		body(r).forEach(a -> names.add(a.get("name").asString()));
		return names;
	}

	@Test
	void buildersAdminsAndOwnersCanCreateAppsButEditorsAndViewersCannot() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		assertThat(createApp(ada.access(), "By owner")).isEqualTo(201);
		assertThat(createApp(memberWithRole(ada.workspaceId(), "admin@example.com", "admin"), "By admin")).isEqualTo(201);
		assertThat(createApp(memberWithRole(ada.workspaceId(), "builder@example.com", "builder"), "By builder")).isEqualTo(201);
		assertThat(createApp(memberWithRole(ada.workspaceId(), "editor@example.com", "editor"), "By editor")).isEqualTo(403);
		assertThat(createApp(memberWithRole(ada.workspaceId(), "viewer@example.com", "viewer"), "By viewer")).isEqualTo(403);

		assertThat(appNames(ada.access())).containsExactlyInAnyOrder("By owner", "By admin", "By builder");
	}

	@Test
	void everyMemberSeesTheWorkspacesAppsIncludingReadOnlyOnes() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		createApp(ada.access(), "Expenses");

		assertThat(appNames(memberWithRole(ada.workspaceId(), "viewer@example.com", "viewer"))).containsExactly("Expenses");
		assertThat(appNames(memberWithRole(ada.workspaceId(), "editor@example.com", "editor"))).containsExactly("Expenses");
	}

	@Test
	void theListShowsOnlyTheActiveWorkspacesAppsAndSwitchingChangesIt() throws Exception {
		Owner first = ownerOfNewWorkspace("ada@example.com", "First");
		createApp(first.access(), "In first");
		// the same person creates a second workspace, which becomes the active one
		MvcResult second = send(post("/api/workspaces"), first.access(), "{\"name\":\"Second\"}");
		Cookie inSecond = second.getResponse().getCookie("tessle_at");
		createApp(inSecond, "In second");
		UUID secondId = UUID.fromString(body(second).get("id").asString());
		Owner stranger = ownerOfNewWorkspace("grace@example.com", "Grace's");
		createApp(stranger.access(), "Not yours");

		assertThat(appNames(inSecond)).containsExactly("In second");
		Cookie backInFirst = send(post("/api/workspaces/" + first.workspaceId() + "/activate"), inSecond, null)
				.getResponse().getCookie("tessle_at");
		assertThat(appNames(backInFirst)).containsExactly("In first");
		assertThat(secondId).isNotEqualTo(first.workspaceId());
	}

	@Test
	void appsNeedASessionAnActiveWorkspaceCsrfAndAValidName() throws Exception {
		mvc.perform(get("/api/apps")).andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));

		TestSessions.Browser nobody = sessions.signIn("nobody@example.com");
		assertThat(mvc.perform(get("/api/apps").cookie(nobody.access())).andReturn().getResponse().getStatus()).isEqualTo(400);

		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		assertThat(mvc.perform(post("/api/apps").cookie(ada.access()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"No CSRF\"}")).andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(createApp(ada.access(), "  ")).isEqualTo(400);
		assertThat(createApp(ada.access(), "x".repeat(81))).isEqualTo(400);
		assertThat(send(post("/api/apps"), ada.access(), "{\"name\":\"Bad\\r\\nname\"}").getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	void appNamesMustBeUniqueWithinAWorkspaceButNotAcrossWorkspaces() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Owner grace = ownerOfNewWorkspace("grace@example.com", "Grace's");

		assertThat(createApp(ada.access(), "Expenses")).isEqualTo(201);
		assertThat(createApp(ada.access(), "  expenses  ")).as("case and padding do not make a new name").isEqualTo(409);
		assertThat(createApp(grace.access(), "Expenses")).isEqualTo(201);
	}

	@Test
	void namesThatLookBlankOrCarryHiddenControlCharactersAreRejectedWithA400() throws Exception {
		Owner ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		assertThat(send(post("/api/apps"), ada.access(), "{\"name\":\"\\u00a0\\u00a0\"}").getResponse().getStatus())
				.as("only non-breaking spaces").isEqualTo(400);
		assertThat(send(post("/api/apps"), ada.access(), "{\"name\":\"a\\u0085b\"}").getResponse().getStatus())
				.as("C1 control character").isEqualTo(400);
		assertThat(send(post("/api/workspaces"), ada.access(), "{\"name\":\"a\\u0085b\"}").getResponse().getStatus()).isEqualTo(400);
	}

}
