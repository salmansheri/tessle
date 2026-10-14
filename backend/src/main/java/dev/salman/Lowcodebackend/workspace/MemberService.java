package dev.salman.Lowcodebackend.workspace;

import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.system.AfterCommit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Membership changes. Admins manage members; only owners touch owners; a Workspace keeps one owner. */
@Service
class MemberService {

	record MemberView(UUID userId, String email, String role) {
	}

	private final JdbcClient jdbc;
	private final MembershipRoles roles;

	MemberService(JdbcClient jdbc, MembershipRoles roles) {
		this.jdbc = jdbc;
		this.roles = roles;
	}

	@Transactional(readOnly = true)
	List<MemberView> list(UUID workspace) {
		return jdbc.sql("""
				select m.user_id, u.email, m.role
				from memberships m join users u on u.id = m.user_id
				where m.workspace_id = ?
				order by m.created_at, u.email
				""").params(workspace).query(MemberView.class).list();
	}

	@Transactional
	void changeRole(UUID actor, UUID workspace, UUID target, Role newRole) {
		Role actorRole = roles.require(actor, workspace, Role.admin);
		Role current = lockAndCurrentRole(workspace, target);
		if ((current == Role.owner || newRole == Role.owner) && actorRole != Role.owner) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners can change owners");
		}
		if (current == Role.owner && newRole != Role.owner) {
			requireAnotherOwner(workspace);
		}
		jdbc.sql("update memberships set role = ? where workspace_id = ? and user_id = ?")
				.params(newRole.name(), workspace, target).update();
		AfterCommit.run(() -> roles.forget(workspace, target));
	}

	@Transactional
	void remove(UUID actor, UUID workspace, UUID target) {
		Role actorRole = roles.require(actor, workspace, Role.admin);
		Role current = lockAndCurrentRole(workspace, target);
		if (current == Role.owner && actorRole != Role.owner) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners can remove owners");
		}
		if (current == Role.owner) {
			requireAnotherOwner(workspace);
		}
		jdbc.sql("delete from memberships where workspace_id = ? and user_id = ?").params(workspace, target).update();
		AfterCommit.run(() -> roles.forget(workspace, target));
	}

	/** Serializes membership changes per Workspace, so two owners cannot demote each other at once. */
	private Role lockAndCurrentRole(UUID workspace, UUID target) {
		WorkspaceLock.acquire(jdbc, workspace);
		return jdbc.sql("select role from memberships where workspace_id = ? and user_id = ?")
				.params(workspace, target).query(String.class).optional().flatMap(Role::parse)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private void requireAnotherOwner(UUID workspace) {
		Integer owners = jdbc.sql("select count(*) from memberships where workspace_id = ? and role = 'owner'")
				.param(workspace).query(Integer.class).single();
		if (owners == null || owners <= 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A workspace must keep at least one owner");
		}
	}

}
