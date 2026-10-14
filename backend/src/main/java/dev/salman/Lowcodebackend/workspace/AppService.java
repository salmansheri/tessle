package dev.salman.Lowcodebackend.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Apps inside the active Workspace. The role checks are the first line; row-level security is the backstop. */
@Service
class AppService {

	record AppView(UUID id, String name, Instant createdAt) {
	}

	private final JdbcClient jdbc;
	private final MembershipRoles roles;

	AppService(JdbcClient jdbc, MembershipRoles roles) {
		this.jdbc = jdbc;
		this.roles = roles;
	}

	@Transactional
	AppView create(UUID user, UUID workspace, String name) {
		roles.require(user, workspace, Role.builder);
		UUID id = UUID.randomUUID();
		String trimmed = name.trim();
		return jdbc.sql("""
				insert into apps (id, workspace_id, name, created_by) values (?, ?, ?, ?)
				returning id, name, created_at
				""").params(id, workspace, trimmed, user)
				.query((rs, n) -> new AppView(rs.getObject("id", UUID.class), rs.getString("name"),
						rs.getTimestamp("created_at").toInstant()))
				.single();
	}

	@Transactional(readOnly = true)
	List<AppView> list(UUID user, UUID workspace) {
		roles.require(user, workspace, Role.viewer);
		return jdbc.sql("select id, name, created_at from apps where workspace_id = ? order by created_at, name")
				.param(workspace).query((rs, n) -> new AppView(rs.getObject("id", UUID.class), rs.getString("name"),
						rs.getTimestamp("created_at").toInstant()))
				.list();
	}

}
