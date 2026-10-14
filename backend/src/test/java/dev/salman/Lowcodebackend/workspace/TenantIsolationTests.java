package dev.salman.Lowcodebackend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Row-level security, exercised below the HTTP layer so nothing in the application can filter for it.
 * Fixture rows are written with the privileged connection (outside any transaction), which bypasses RLS.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantIsolationTests {

	@Autowired
	RlsProbe probe;

	@Autowired
	JdbcTemplate admin;

	@MockitoBean
	JavaMailSender mail;

	UUID alice = UUID.randomUUID();
	UUID bob = UUID.randomUUID();
	UUID wsAlice = UUID.randomUUID();
	UUID wsBob = UUID.randomUUID();

	@BeforeEach
	void fixtures() {
		admin.update("delete from workspaces");
		admin.update("delete from users");
		admin.update("insert into users (id, email) values (?, 'alice@example.com'), (?, 'bob@example.com')", alice, bob);
		admin.update("insert into workspaces (id, name, created_by) values (?, 'Alice Inc', ?), (?, 'Bob Ltd', ?)",
				wsAlice, alice, wsBob, bob);
		admin.update("insert into memberships (workspace_id, user_id, role) values (?, ?, 'owner'), (?, ?, 'owner')",
				wsAlice, alice, wsBob, bob);
	}

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	/** Authenticates as the user, optionally with an active Workspace claim (which may be forged). */
	static void actAs(UUID user, UUID activeWorkspace) {
		Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "none").subject(user.toString());
		if (activeWorkspace != null) {
			jwt.claim("wid", activeWorkspace.toString());
		}
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
	}

	@Test
	void aQueryThatOmitsAnyFilterOnlyReturnsTheCallersWorkspaces() {
		actAs(alice, null);

		assertThat(probe.allWorkspaceIds()).containsExactly(wsAlice);
		assertThat(probe.allMemberships()).containsExactly(wsAlice + ":" + alice);
	}

	@Test
	void withoutAnAuthenticatedUserNothingIsVisibleEvenThoughRowsExist() {
		assertThat(admin.queryForObject("select count(*) from workspaces", Integer.class)).isEqualTo(2);

		assertThat(probe.allWorkspaceIds()).isEmpty();
		assertThat(probe.allMemberships()).isEmpty();
	}

	@Test
	void aForgedActiveWorkspaceClaimDoesNotExposeAnotherWorkspace() {
		actAs(alice, wsBob);

		assertThat(probe.allWorkspaceIds()).containsExactly(wsAlice);
		assertThat(probe.allMemberships()).containsExactly(wsAlice + ":" + alice);
	}

	@Test
	void aMemberOfTheActiveWorkspaceSeesItsOtherMembers() {
		UUID carol = UUID.randomUUID();
		admin.update("insert into users (id, email) values (?, 'carol@example.com')", carol);
		admin.update("insert into memberships (workspace_id, user_id, role) values (?, ?, 'viewer')", wsAlice, carol);

		actAs(alice, wsAlice);
		assertThat(probe.allMemberships()).containsExactlyInAnyOrder(wsAlice + ":" + alice, wsAlice + ":" + carol);

		actAs(alice, null);
		assertThat(probe.allMemberships()).as("only her own membership without an active workspace")
				.containsExactly(wsAlice + ":" + alice);
	}

	@Test
	void aUserCannotJoinAWorkspaceTheyAreNotAnAdminOf() {
		actAs(alice, wsBob);

		assertThatThrownBy(() -> probe.insertMembership(wsBob, alice, "viewer")).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> probe.insertMembership(wsBob, alice, "owner")).isInstanceOf(DataAccessException.class);
	}

	@Test
	void aUserCannotCreateAWorkspaceOnSomeoneElsesBehalf() {
		actAs(alice, null);

		assertThatThrownBy(() -> probe.insertWorkspace(UUID.randomUUID(), "Forged", bob)).isInstanceOf(DataAccessException.class);
	}

	@Test
	void theCreatorOfAnEmptyWorkspaceCanBecomeItsOwnerButOnlyOnce() {
		UUID fresh = UUID.randomUUID();
		actAs(alice, null);
		probe.insertWorkspace(fresh, "Fresh", alice);

		probe.insertMembership(fresh, alice, "owner");

		assertThat(probe.allWorkspaceIds()).containsExactlyInAnyOrder(wsAlice, fresh);
		actAs(bob, null);
		assertThatThrownBy(() -> probe.insertMembership(fresh, bob, "owner")).isInstanceOf(DataAccessException.class);
	}

	@Test
	void creatingAWorkspaceForYourselfAndClaimingOwnershipOfSomeoneElsesIsRefused() {
		actAs(alice, null);

		assertThatThrownBy(() -> probe.insertMembership(wsBob, alice, "owner")).isInstanceOf(DataAccessException.class);
	}

	/** Adds an admin and a viewer to Alice's workspace through the privileged connection. */
	private UUID[] addAdminAndViewer() {
		UUID dave = UUID.randomUUID();
		UUID erin = UUID.randomUUID();
		admin.update("insert into users (id, email) values (?, 'dave@example.com'), (?, 'erin@example.com')", dave, erin);
		admin.update("insert into memberships (workspace_id, user_id, role) values (?, ?, 'admin'), (?, ?, 'viewer')",
				wsAlice, dave, wsAlice, erin);
		return new UUID[] { dave, erin };
	}

	@Test
	void anAdminCannotPromoteThemselvesToOwner() {
		UUID dave = addAdminAndViewer()[0];
		actAs(dave, wsAlice);

		assertThatThrownBy(() -> probe.updateRole(wsAlice, dave, "owner")).isInstanceOf(DataAccessException.class);
	}

	@Test
	void anAdminCannotRemoveOrDemoteAnOwner() {
		UUID dave = addAdminAndViewer()[0];
		actAs(dave, wsAlice);

		assertThat(probe.deleteMembership(wsAlice, alice)).isZero();
		assertThat(probe.updateRole(wsAlice, alice, "viewer")).isZero();
	}

	@Test
	void anAdminCannotCreateOwnersButCanManageOtherRoles() {
		UUID[] people = addAdminAndViewer();
		UUID dave = people[0];
		UUID erin = people[1];
		actAs(dave, wsAlice);

		assertThatThrownBy(() -> probe.updateRole(wsAlice, erin, "owner")).isInstanceOf(DataAccessException.class);
		assertThat(probe.updateRole(wsAlice, erin, "editor")).isEqualTo(1);
		assertThat(probe.deleteMembership(wsAlice, erin)).isEqualTo(1);
	}

	@Test
	void anOwnerCanManageAdminsAndOtherOwners() {
		UUID dave = addAdminAndViewer()[0];
		actAs(alice, wsAlice);

		assertThat(probe.updateRole(wsAlice, dave, "owner")).isEqualTo(1);
		assertThat(probe.updateRole(wsAlice, dave, "admin")).isEqualTo(1);
	}

	@Test
	void aViewerCannotChangeAnyMembership() {
		UUID[] people = addAdminAndViewer();
		UUID erin = people[1];
		actAs(erin, wsAlice);

		assertThat(probe.updateRole(wsAlice, erin, "admin")).isZero();
		assertThat(probe.deleteMembership(wsAlice, alice)).isZero();
	}

	@Test
	void appsAreScopedToTheActiveWorkspaceAndOnlyBuildersAndAboveCanWriteThem() {
		UUID[] people = addAdminAndViewer();
		UUID dave = people[0];
		UUID erin = people[1];
		admin.update("insert into apps (id, workspace_id, name, created_by) values (?, ?, 'Alice app', ?), (?, ?, 'Bob app', ?)",
				UUID.randomUUID(), wsAlice, alice, UUID.randomUUID(), wsBob, bob);

		actAs(alice, wsAlice);
		assertThat(probe.allAppNames()).as("a query without any filter").containsExactly("Alice app");

		actAs(alice, null);
		assertThat(probe.allAppNames()).as("no active workspace").isEmpty();

		actAs(alice, wsBob);
		assertThat(probe.allAppNames()).as("forged active workspace").isEmpty();
		assertThatThrownBy(() -> probe.insertApp(wsBob, "Planted", alice)).isInstanceOf(DataAccessException.class);

		actAs(erin, wsAlice); // viewer
		assertThat(probe.allAppNames()).containsExactly("Alice app");
		assertThatThrownBy(() -> probe.insertApp(wsAlice, "Viewer app", erin)).isInstanceOf(DataAccessException.class);

		actAs(dave, wsAlice); // admin
		probe.insertApp(wsAlice, "Admin app", dave);
		assertThat(probe.allAppNames()).containsExactlyInAnyOrder("Alice app", "Admin app");
	}

	@Test
	void onlyBuildersAndAboveCanChangeOrDeleteAppsAndOnlyInTheirActiveWorkspace() {
		UUID[] people = addAdminAndViewer();
		UUID dave = people[0];
		UUID erin = people[1];
		admin.update("insert into apps (id, workspace_id, name, created_by) values (?, ?, 'Alice app', ?), (?, ?, 'Bob app', ?)",
				UUID.randomUUID(), wsAlice, alice, UUID.randomUUID(), wsBob, bob);

		actAs(erin, wsAlice); // viewer
		assertThat(probe.renameApps("Hacked")).isZero();
		assertThat(probe.deleteAllApps()).isZero();

		actAs(dave, wsAlice); // admin: may change Alice's app, can't even see Bob's
		assertThat(probe.renameApps("Renamed")).isEqualTo(1);

		actAs(alice, wsBob); // forged active workspace
		assertThat(probe.renameApps("Hacked")).isZero();
		assertThat(probe.deleteAllApps()).isZero();

		actAs(alice, wsAlice);
		assertThat(probe.deleteAllApps()).isEqualTo(1);
		assertThat(admin.queryForList("select name from apps", String.class)).containsExactly("Bob app");
	}

}
