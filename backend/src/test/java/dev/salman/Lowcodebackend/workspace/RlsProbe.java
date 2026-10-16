package dev.salman.Lowcodebackend.workspace;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately filter-free queries, run through the same transactional path the application uses. */
@Component
class RlsProbe {

	@Autowired
	JdbcClient jdbc;

	@Transactional(readOnly = true)
	List<UUID> allWorkspaceIds() {
		return jdbc.sql("select id from workspaces").query(UUID.class).list();
	}

	@Transactional(readOnly = true)
	List<String> allMemberships() {
		return jdbc.sql("select workspace_id || ':' || user_id from memberships").query(String.class).list();
	}

	@Transactional
	void insertMembership(UUID workspace, UUID user, String role) {
		jdbc.sql("insert into memberships (workspace_id, user_id, role) values (?, ?, ?)")
				.params(workspace, user, role).update();
	}

	@Transactional
	void insertWorkspace(UUID id, String name, UUID createdBy) {
		jdbc.sql("insert into workspaces (id, name, created_by) values (?, ?, ?)").params(id, name, createdBy).update();
	}

	@Transactional
	int updateRole(UUID workspace, UUID user, String role) {
		return jdbc.sql("update memberships set role = ? where workspace_id = ? and user_id = ?")
				.params(role, workspace, user).update();
	}

	@Transactional
	int deleteMembership(UUID workspace, UUID user) {
		return jdbc.sql("delete from memberships where workspace_id = ? and user_id = ?").params(workspace, user).update();
	}

	@Transactional(readOnly = true)
	List<String> allAppNames() {
		return jdbc.sql("select name from apps order by name").query(String.class).list();
	}

	@Transactional
	void insertApp(UUID workspace, String name, UUID createdBy) {
		jdbc.sql("insert into apps (id, workspace_id, name, created_by) values (?, ?, ?, ?)")
				.params(UUID.randomUUID(), workspace, name, createdBy).update();
	}

	@Transactional
	int renameApps(String newName) {
		return jdbc.sql("update apps set name = ?").params(newName).update();
	}

	@Transactional
	int deleteAllApps() {
		return jdbc.sql("delete from apps").update();
	}

	@Transactional(readOnly = true)
	List<String> allDataSourceNames() {
		return jdbc.sql("select logical_name from data_sources order by logical_name").query(String.class).list();
	}

	@Transactional
	void insertDataSource(UUID workspace, String logicalName, UUID createdBy) {
		jdbc.sql("""
				insert into data_sources (id, workspace_id, logical_name, label, type, host, port, database_name, username,
				                          password_ciphertext, secret_key_id, created_by)
				values (?, ?, ?, 'Label', 'postgresql', 'db.example.com', 5432, 'd', 'u', ?, 'k1', ?)
				""").params(UUID.randomUUID(), workspace, logicalName, new byte[] { 1 }, createdBy).update();
	}

	@Transactional
	int renameDataSources(String label) {
		return jdbc.sql("update data_sources set label = ?").params(label).update();
	}

	@Transactional
	int deleteAllDataSources() {
		return jdbc.sql("delete from data_sources").update();
	}

	@Transactional
	void recordAudit(UUID workspace, UUID actor) {
		jdbc.sql("insert into audit_events (id, workspace_id, actor_id, action, target_type) values (?, ?, ?, 'x', 'y')")
				.params(UUID.randomUUID(), workspace, actor).update();
	}

	@Transactional(readOnly = true)
	int auditEventCount() {
		return jdbc.sql("select count(*) from audit_events").query(Integer.class).single();
	}

	@Transactional
	int rewriteAuditEvents() {
		return jdbc.sql("update audit_events set action = 'rewritten'").update();
	}

	@Transactional
	int deleteAuditEvents() {
		return jdbc.sql("delete from audit_events").update();
	}

}
