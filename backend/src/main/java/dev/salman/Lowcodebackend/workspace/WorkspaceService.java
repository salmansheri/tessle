package dev.salman.Lowcodebackend.workspace;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method runs in a tenant-scoped transaction (see TenantTransactionManager), so row-level security
 * decides what is visible and writable; the explicit user filters below are for the join, not for safety.
 */
@Service
class WorkspaceService {

	record WorkspaceView(UUID id, String name, String role) {
	}

	private final JdbcClient jdbc;

	WorkspaceService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	WorkspaceView create(UUID user, String name) {
		UUID id = UUID.randomUUID();
		String trimmed = name.trim();
		jdbc.sql("insert into workspaces (id, name, created_by) values (?, ?, ?)").params(id, trimmed, user).update();
		jdbc.sql("insert into memberships (workspace_id, user_id, role) values (?, ?, 'owner')").params(id, user).update();
		return new WorkspaceView(id, trimmed, "owner");
	}

	@Transactional(readOnly = true)
	List<WorkspaceView> listFor(UUID user) {
		return jdbc.sql("""
				select w.id, w.name, m.role
				from workspaces w join memberships m on m.workspace_id = w.id
				where m.user_id = ?
				order by w.created_at, w.name
				""").params(user).query(WorkspaceView.class).list();
	}

	@Transactional(readOnly = true)
	boolean isMember(UUID user, UUID workspace) {
		return jdbc.sql("select exists (select 1 from memberships where workspace_id = ? and user_id = ?)")
				.params(workspace, user).query(Boolean.class).single();
	}

}
