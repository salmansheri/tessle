package dev.salman.Lowcodebackend.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import dev.salman.Lowcodebackend.identity.RateLimiter;
import dev.salman.Lowcodebackend.mail.MailMessage;
import dev.salman.Lowcodebackend.mail.MailQueue;
import dev.salman.Lowcodebackend.system.AfterCommit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class InvitationService {

	static final Duration TTL = Duration.ofDays(7);

	record InvitationView(UUID id, String email, String role, Instant expiresAt) {
	}

	record Accepted(UUID workspaceId, String role) {
	}

	private final JdbcClient jdbc;
	private final MembershipRoles roles;
	private final MailQueue mailQueue;
	private final RateLimiter limiter;
	private final String publicUrl;
	private final SecureRandom random = new SecureRandom();

	InvitationService(JdbcClient jdbc, MembershipRoles roles, MailQueue mailQueue, RateLimiter limiter,
			@Value("${tessle.public-url}") String publicUrl) {
		this.jdbc = jdbc;
		this.roles = roles;
		this.mailQueue = mailQueue;
		this.limiter = limiter;
		this.publicUrl = publicUrl.replaceAll("/+$", "");
	}

	@Transactional
	InvitationView invite(UUID actor, UUID workspace, String rawEmail, Role role) {
		Role actorRole = roles.require(actor, workspace, Role.admin);
		if (role == Role.owner && actorRole != Role.owner) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners can invite owners");
		}
		// Every invitation sends mail from our domain to an arbitrary address, so cap the volume per Workspace.
		if (!limiter.allow("invite-workspace", workspace.toString(), 20, Duration.ofHours(1))) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many invitations");
		}
		String email = rawEmail.trim().toLowerCase(Locale.ROOT);
		WorkspaceLock.acquire(jdbc, workspace);
		boolean alreadyMember = jdbc.sql("""
				select exists (select 1 from memberships m join users u on u.id = m.user_id
				               where m.workspace_id = ? and lower(u.email) = ?)
				""").params(workspace, email).query(Boolean.class).single();
		if (alreadyMember) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a member");
		}

		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		UUID id = UUID.randomUUID();

		jdbc.sql("delete from invitations where workspace_id = ? and lower(email) = ? and accepted_at is null")
				.params(workspace, email).update();
		// Expiry is computed by the database, the same clock that later judges it.
		Instant expires = jdbc.sql("""
				insert into invitations (id, workspace_id, email, role, token_hash, invited_by, expires_at)
				values (?, ?, ?, ?, ?, ?, now() + make_interval(secs => ?))
				returning expires_at
				""").params(id, workspace, email, role.name(), hash(token), actor, TTL.toSeconds())
				.query((rs, n) -> rs.getTimestamp("expires_at").toInstant()).single();

		String workspaceName = jdbc.sql("select name from workspaces where id = ?").param(workspace).query(String.class).single();
		String inviter = jdbc.sql("select email from users where id = ?").param(actor).query(String.class).single();
		MailMessage mail = MailMessage.of(email, "You are invited to " + workspaceName + " on Tessle",
				inviter + " invited you to join \"" + workspaceName + "\" as " + role.name() + ".\n\n"
						+ "Sign in with this email address and accept: " + publicUrl + "/invitations/accept?token=" + token
						+ "\n\nThe invitation expires in 7 days.");
		AfterCommit.run(() -> mailQueue.enqueue(mail));
		return new InvitationView(id, email, role.name(), expires);
	}

	@Transactional(readOnly = true)
	List<InvitationView> pending(UUID actor, UUID workspace) {
		roles.require(actor, workspace, Role.admin);
		return jdbc.sql("""
				select id, email, role, expires_at from invitations
				where workspace_id = ? and accepted_at is null and expires_at > now()
				order by created_at
				""").params(workspace).query((rs, n) -> new InvitationView(rs.getObject("id", UUID.class),
						rs.getString("email"), rs.getString("role"), rs.getTimestamp("expires_at").toInstant())).list();
	}

	@Transactional
	void revoke(UUID actor, UUID workspace, UUID id) {
		roles.require(actor, workspace, Role.admin);
		int deleted = jdbc.sql("delete from invitations where id = ? and workspace_id = ? and accepted_at is null")
				.params(id, workspace).update();
		if (deleted == 0) {
			// Visible to admins but not deletable means a pending owner invitation, which only owners may withdraw.
			boolean visible = jdbc.sql("select exists (select 1 from invitations where id = ? and workspace_id = ? and accepted_at is null)")
					.params(id, workspace).query(Boolean.class).single();
			throw new ResponseStatusException(visible ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND);
		}
	}

	/** Empty for any reason the invitation cannot be used: unknown, used, expired, or meant for someone else. */
	@Transactional
	Optional<Accepted> accept(UUID user, String token) {
		Optional<Accepted> accepted = jdbc.sql("select ws_id, member_role from accept_invitation(?)").param(hash(token))
				.query((rs, n) -> new Accepted(rs.getObject("ws_id", UUID.class), rs.getString("member_role"))).optional();
		accepted.ifPresent(a -> AfterCommit.run(() -> roles.forget(a.workspaceId(), user)));
		return accepted;
	}

	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
