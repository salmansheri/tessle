package dev.salman.Lowcodebackend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import dev.salman.Lowcodebackend.identity.TestSessions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
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
class MembersApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestSessions sessions;

	@Autowired
	JdbcTemplate admin;

	@Autowired
	StringRedisTemplate redis;

	@MockitoBean
	JavaMailSender mail;

	final ObjectMapper json = new ObjectMapper();

	/** A signed-in person acting in a workspace: the access cookie names the active workspace. */
	record Actor(UUID userId, String email, Cookie access) {
	}

	@BeforeEach
	void clean() {
		admin.update("delete from invitations");
		admin.update("delete from workspaces");
		admin.update("delete from users");
		sessions.flushRedis();
		reset(mail);
	}

	Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}

	MvcResult send(MockHttpServletRequestBuilder request, Cookie access, String body) throws Exception {
		Cookie csrf = csrf();
		request = request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
		if (access != null) {
			request = request.cookie(access);
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request).andReturn();
	}

	int status(MvcResult r) {
		return r.getResponse().getStatus();
	}

	JsonNode body(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	/** Signs in as a new person and creates a workspace, becoming its owner and acting in it. */
	Actor ownerOfNewWorkspace(String email, String workspaceName) throws Exception {
		TestSessions.Browser browser = sessions.signIn(email);
		MvcResult created = send(post("/api/workspaces"), browser.access(), "{\"name\":\"" + workspaceName + "\"}");
		assertThat(status(created)).isEqualTo(201);
		return new Actor(browser.userId(), email, created.getResponse().getCookie("tessle_at"));
	}

	UUID workspaceOf(Actor actor) throws Exception {
		return UUID.fromString(body(mvc.perform(get("/api/auth/me").cookie(actor.access()))
				.andReturn()).get("activeWorkspaceId").asString());
	}

	/** Invites by email, reads the token out of the mail the consumer sent, and has the invitee accept it. */
	Actor join(Actor inviter, String email, String role) throws Exception {
		MvcResult invited = send(post("/api/invitations"), inviter.access(), "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}");
		assertThat(status(invited)).isEqualTo(201);
		TestSessions.Browser browser = sessions.signIn(email);
		MvcResult accepted = send(post("/api/invitations/accept"), browser.access(), "{\"token\":\"" + lastTokenFor(email) + "\"}");
		assertThat(status(accepted)).isEqualTo(200);
		return new Actor(browser.userId(), email, accepted.getResponse().getCookie("tessle_at"));
	}

	String lastTokenFor(String email) {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(mail, atLeastOnce()).send(captor.capture()));
		List<SimpleMailMessage> sent = captor.getAllValues().stream()
				.filter(m -> m.getTo() != null && m.getTo().length == 1 && m.getTo()[0].equals(email)).toList();
		assertThat(sent).isNotEmpty();
		Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(sent.get(sent.size() - 1).getText());
		assertThat(m.find()).as("the mail contains an acceptance link").isTrue();
		return m.group(1);
	}

	int invite(Actor by, String email, String role) throws Exception {
		return status(send(post("/api/invitations"), by.access(), "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"));
	}

	int changeRole(Actor by, Actor target, String role) throws Exception {
		return status(send(patch("/api/members/" + target.userId()), by.access(), "{\"role\":\"" + role + "\"}"));
	}

	int remove(Actor by, Actor target) throws Exception {
		return status(send(delete("/api/members/" + target.userId()), by.access(), null));
	}

	String roleOf(Actor viewer, Actor target) throws Exception {
		JsonNode members = body(mvc.perform(get("/api/members").cookie(viewer.access())).andReturn());
		for (JsonNode m : members) {
			if (m.get("userId").asString().equals(target.userId().toString())) {
				return m.get("role").asString();
			}
		}
		return null;
	}

	@Test
	void anInvitationIsMailedAndAcceptingItAfterSigningInCreatesTheMembership() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		Actor grace = join(ada, "grace@example.com", "editor");

		assertThat(roleOf(grace, grace)).isEqualTo("editor");
		assertThat(roleOf(ada, grace)).isEqualTo("editor");
		assertThat(roleOf(ada, ada)).isEqualTo("owner");
		assertThat(workspaceOf(grace)).isEqualTo(workspaceOf(ada));
	}

	@Test
	void theEmailedTokenIsStoredOnlyAsAHash() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		invite(ada, "grace@example.com", "viewer");
		String token = lastTokenFor("grace@example.com");

		String stored = admin.queryForObject("select token_hash from invitations", String.class);

		assertThat(stored).isNotEqualTo(token).doesNotContain(token);
	}

	@Test
	void invitationsCannotBeAcceptedByTheWrongPersonTwiceExpiredOrWhenUnknown() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		invite(ada, "grace@example.com", "viewer");
		String token = lastTokenFor("grace@example.com");

		TestSessions.Browser mallory = sessions.signIn("mallory@example.com");
		assertThat(status(send(post("/api/invitations/accept"), mallory.access(), "{\"token\":\"" + token + "\"}")))
				.as("someone else's invitation").isEqualTo(404);
		assertThat(status(send(post("/api/invitations/accept"), mallory.access(), "{\"token\":\"nope\"}"))).isEqualTo(404);

		TestSessions.Browser grace = sessions.signIn("grace@example.com");
		admin.update("update invitations set expires_at = now() - interval '1 second'");
		assertThat(status(send(post("/api/invitations/accept"), grace.access(), "{\"token\":\"" + token + "\"}")))
				.as("expired").isEqualTo(404);

		admin.update("update invitations set expires_at = now() + interval '1 day'");
		assertThat(status(send(post("/api/invitations/accept"), grace.access(), "{\"token\":\"" + token + "\"}"))).isEqualTo(200);
		assertThat(status(send(post("/api/invitations/accept"), grace.access(), "{\"token\":\"" + token + "\"}")))
				.as("already used").isEqualTo(404);
	}

	@Test
	void onlyOwnersAndAdminsCanInviteAndOnlyOwnersCanInviteOwners() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Actor admin1 = join(ada, "admin@example.com", "admin");
		Actor editor = join(ada, "editor@example.com", "editor");

		assertThat(invite(editor, "x1@example.com", "viewer")).isEqualTo(403);
		assertThat(invite(admin1, "x2@example.com", "viewer")).isEqualTo(201);
		assertThat(invite(admin1, "x3@example.com", "owner")).isEqualTo(403);
		assertThat(invite(ada, "x4@example.com", "owner")).isEqualTo(201);
	}

	@Test
	void invitingSomeoneAlreadyAMemberIsAConflictAndReinvitingReplacesThePendingOne() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		join(ada, "grace@example.com", "viewer");
		assertThat(invite(ada, "grace@example.com", "editor")).isEqualTo(409);

		assertThat(invite(ada, "new@example.com", "viewer")).isEqualTo(201);
		String first = lastTokenFor("new@example.com");
		reset(mail);
		assertThat(invite(ada, "new@example.com", "viewer")).isEqualTo(201);
		String second = lastTokenFor("new@example.com");
		assertThat(second).isNotEqualTo(first);

		TestSessions.Browser invitee = sessions.signIn("new@example.com");
		assertThat(status(send(post("/api/invitations/accept"), invitee.access(), "{\"token\":\"" + first + "\"}"))).isEqualTo(404);
		assertThat(status(send(post("/api/invitations/accept"), invitee.access(), "{\"token\":\"" + second + "\"}"))).isEqualTo(200);
	}

	@Test
	void pendingInvitationsAreListedAndCanBeRevoked() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		invite(ada, "grace@example.com", "viewer");
		String token = lastTokenFor("grace@example.com");

		JsonNode pending = body(mvc.perform(get("/api/invitations").cookie(ada.access())).andReturn());
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).get("email").asString()).isEqualTo("grace@example.com");
		assertThat(status(send(delete("/api/invitations/" + pending.get(0).get("id").asString()), ada.access(), null))).isEqualTo(204);

		TestSessions.Browser grace = sessions.signIn("grace@example.com");
		assertThat(status(send(post("/api/invitations/accept"), grace.access(), "{\"token\":\"" + token + "\"}"))).isEqualTo(404);
	}

	@Test
	void ownersAndAdminsChangeRolesButAdminsCannotTouchOwners() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Actor boss = join(ada, "boss@example.com", "owner");
		Actor admin1 = join(ada, "admin@example.com", "admin");
		Actor editor = join(ada, "editor@example.com", "editor");
		Actor viewer = join(ada, "viewer@example.com", "viewer");

		assertThat(changeRole(ada, editor, "builder")).isEqualTo(204);
		assertThat(roleOf(ada, editor)).isEqualTo("builder");
		assertThat(changeRole(admin1, viewer, "editor")).isEqualTo(204);
		assertThat(changeRole(admin1, viewer, "owner")).as("admins cannot create owners").isEqualTo(403);
		assertThat(changeRole(admin1, boss, "viewer")).as("admins cannot demote owners").isEqualTo(403);
		assertThat(changeRole(admin1, admin1, "owner")).as("admins cannot promote themselves").isEqualTo(403);
		assertThat(changeRole(editor, viewer, "admin")).as("editors cannot manage roles").isEqualTo(403);
		assertThat(changeRole(ada, boss, "admin")).as("owners can demote owners").isEqualTo(204);
	}

	@Test
	void removingAMemberTakesEffectImmediately() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Actor admin1 = join(ada, "admin@example.com", "admin");
		Actor editor = join(ada, "editor@example.com", "editor");
		assertThat(roleOf(editor, editor)).isEqualTo("editor");

		assertThat(remove(admin1, editor)).isEqualTo(204);

		assertThat(status(mvc.perform(get("/api/members").cookie(editor.access())).andReturn())).isEqualTo(403);
		assertThat(remove(editor, admin1)).isEqualTo(403);
		assertThat(remove(admin1, ada)).as("admins cannot remove owners").isEqualTo(403);
	}

	@Test
	void aDemotionTakesEffectOnTheVeryNextRequestEvenThoughRolesAreCached() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Actor admin1 = join(ada, "admin@example.com", "admin");
		assertThat(invite(admin1, "first@example.com", "viewer")).isEqualTo(201);
		assertThat(redis.hasKey("role:" + workspaceOf(ada) + ":" + admin1.userId())).as("the role is cached").isTrue();

		assertThat(changeRole(ada, admin1, "viewer")).isEqualTo(204);

		assertThat(invite(admin1, "second@example.com", "viewer")).isEqualTo(403);
	}

	@Test
	void theLastOwnerCannotBeRemovedOrDemotedButACoOwnerLetsThemLeave() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		assertThat(changeRole(ada, ada, "admin")).isEqualTo(409);
		assertThat(remove(ada, ada)).isEqualTo(409);

		Actor boss = join(ada, "boss@example.com", "owner");
		assertThat(changeRole(ada, ada, "admin")).isEqualTo(204);
		assertThat(changeRole(boss, boss, "viewer")).as("boss is now the only owner").isEqualTo(409);
		assertThat(remove(boss, boss)).isEqualTo(409);
	}

	@Test
	void theDatabaseItselfRefusesToLeaveAWorkspaceWithoutAnOwner() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		join(ada, "grace@example.com", "viewer");

		assertThatThrownBy(() -> admin.update("update memberships set role = 'viewer' where role = 'owner'"))
				.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> admin.update("delete from memberships where role = 'owner'"))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void membersAndInvitationsNeedASessionAnActiveWorkspaceAndCsrf() throws Exception {
		mvc.perform(get("/api/members")).andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));

		TestSessions.Browser noWorkspace = sessions.signIn("nobody@example.com");
		assertThat(status(mvc.perform(get("/api/members").cookie(noWorkspace.access())).andReturn())).isEqualTo(400);

		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		assertThat(status(mvc.perform(post("/api/invitations").cookie(ada.access()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@example.com\",\"role\":\"viewer\"}")).andReturn())).isEqualTo(403);
		assertThat(send(post("/api/invitations"), ada.access(), "{\"email\":\"not-an-email\",\"role\":\"viewer\"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(send(post("/api/invitations"), ada.access(), "{\"email\":\"a@example.com\",\"role\":\"god\"}").getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	void anAdminCannotReplaceOrWithdrawAPendingOwnerInvitation() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		Actor admin1 = join(ada, "admin@example.com", "admin");
		assertThat(invite(ada, "future@example.com", "owner")).isEqualTo(201);

		assertThat(invite(admin1, "future@example.com", "viewer")).as("cannot replace it").isEqualTo(409);
		String id = body(mvc.perform(get("/api/invitations").cookie(admin1.access())).andReturn()).get(0).get("id").asString();
		assertThat(status(send(delete("/api/invitations/" + id), admin1.access(), null))).as("cannot withdraw it").isEqualTo(403);
		assertThat(status(send(delete("/api/invitations/" + id), ada.access(), null))).as("owners can").isEqualTo(204);
	}

	@Test
	void sendingInvitationsIsRateLimitedPerWorkspace() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");

		for (int i = 0; i < 20; i++) {
			assertThat(invite(ada, "person" + i + "@example.com", "viewer")).isEqualTo(201);
		}
		assertThat(invite(ada, "one-too-many@example.com", "viewer")).isEqualTo(429);
	}

	@Test
	void inviteRequestsRacingForTheSameAddressNeverFail() throws Exception {
		Actor ada = ownerOfNewWorkspace("ada@example.com", "Acme");
		var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
		try {
			List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
			for (int i = 0; i < 4; i++) {
				results.add(pool.submit(() -> invite(ada, "race@example.com", "viewer")));
			}
			for (var result : results) {
				assertThat(result.get()).isEqualTo(201);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(admin.queryForObject("select count(*) from invitations where email = 'race@example.com'", Integer.class)).isEqualTo(1);
	}

	@Test
	void workspaceNamesWithControlCharactersAreRejected() throws Exception {
		TestSessions.Browser ada = sessions.signIn("ada@example.com");

		assertThat(status(send(post("/api/workspaces"), ada.access(), "{\"name\":\"Evil\\r\\nBcc: x@y.z\"}"))).isEqualTo(400);
	}

}
