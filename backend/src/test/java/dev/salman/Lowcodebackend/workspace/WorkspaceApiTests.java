package dev.salman.Lowcodebackend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestSessions.class })
class WorkspaceApiTests {

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

	MvcResult create(Cookie access, String name) throws Exception {
		Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return mvc.perform(post("/api/workspaces").cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")).andReturn();
	}

	MvcResult activate(Cookie access, String workspaceId) throws Exception {
		Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return mvc.perform(post("/api/workspaces/" + workspaceId + "/activate").cookie(access, csrf)
				.header("X-XSRF-TOKEN", csrf.getValue())).andReturn();
	}

	JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	@Test
	void creatingAWorkspaceMakesTheCreatorItsOwnerAndActivatesIt() throws Exception {
		TestSessions.Browser ada = sessions.signIn("ada@example.com");

		MvcResult created = create(ada.access(), "Acme");

		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		JsonNode workspace = body(created);
		assertThat(workspace.get("name").asString()).isEqualTo("Acme");
		assertThat(workspace.get("role").asString()).isEqualTo("owner");

		Cookie renewed = created.getResponse().getCookie("tessle_at");
		assertThat(renewed).as("a new access cookie names the active workspace").isNotNull();
		mvc.perform(get("/api/auth/me").cookie(renewed)).andExpect(status().isOk())
				.andExpect(jsonPath("$.activeWorkspaceId").value(workspace.get("id").asString()));
		mvc.perform(get("/api/workspaces").cookie(renewed)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].role").value("owner"));
	}

	@Test
	void listingShowsOnlyTheWorkspacesTheUserBelongsTo() throws Exception {
		TestSessions.Browser ada = sessions.signIn("ada@example.com");
		TestSessions.Browser grace = sessions.signIn("grace@example.com");
		create(ada.access(), "Ada's");
		create(grace.access(), "Grace's");

		mvc.perform(get("/api/workspaces").cookie(ada.access())).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Ada's"));
		mvc.perform(get("/api/workspaces").cookie(grace.access())).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Grace's"));
	}

	@Test
	void switchingToAMemberWorkspaceChangesTheActiveOneAndForeignOnesAreNotFound() throws Exception {
		TestSessions.Browser ada = sessions.signIn("ada@example.com");
		TestSessions.Browser grace = sessions.signIn("grace@example.com");
		String first = body(create(ada.access(), "First")).get("id").asString();
		String second = body(create(ada.access(), "Second")).get("id").asString();
		String foreign = body(create(grace.access(), "Foreign")).get("id").asString();

		MvcResult switched = activate(ada.access(), first);
		assertThat(switched.getResponse().getStatus()).isEqualTo(204);
		mvc.perform(get("/api/auth/me").cookie(switched.getResponse().getCookie("tessle_at")))
				.andExpect(jsonPath("$.activeWorkspaceId").value(first));

		assertThat(activate(ada.access(), foreign).getResponse().getStatus()).isEqualTo(404);
		assertThat(activate(ada.access(), UUID.randomUUID().toString()).getResponse().getStatus()).isEqualTo(404);
		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void theActiveWorkspaceSurvivesARefresh() throws Exception {
		TestSessions.Browser ada = sessions.signIn("ada@example.com");
		MvcResult created = create(ada.access(), "Sticky");
		String id = body(created).get("id").asString();

		Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(ada.refresh(), csrf)
				.header("X-XSRF-TOKEN", csrf.getValue())).andReturn();

		assertThat(refreshed.getResponse().getStatus()).isEqualTo(204);
		mvc.perform(get("/api/auth/me").cookie(refreshed.getResponse().getCookie("tessle_at")))
				.andExpect(jsonPath("$.activeWorkspaceId").value(id));
	}

	@Test
	void creatingAndListingRequireASessionAndCsrfAndAValidName() throws Exception {
		mvc.perform(get("/api/workspaces")).andExpect(status().isUnauthorized());

		TestSessions.Browser ada = sessions.signIn("ada@example.com");
		mvc.perform(post("/api/workspaces").cookie(ada.access()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"No CSRF\"}")).andExpect(status().isForbidden());
		assertThat(create(ada.access(), "   ").getResponse().getStatus()).isEqualTo(400);
		assertThat(create(ada.access(), "x".repeat(81)).getResponse().getStatus()).isEqualTo(400);
	}

}
