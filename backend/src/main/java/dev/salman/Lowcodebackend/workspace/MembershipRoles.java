package dev.salman.Lowcodebackend.workspace;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The caller's role in a Workspace, read on every request through a short-lived Redis cache.
 * Every change to a Membership calls {@link #forget}, so a demotion applies to the very next request.
 * A per-entry version counter closes the read/invalidate race: a role read from the database before an
 * invalidation is only cached if no invalidation happened since. The TTL bounds staleness if an
 * invalidation is ever lost (Redis down). Row-level security remains the backstop for writes.
 */
@Component
public class MembershipRoles {

	private static final Logger log = LoggerFactory.getLogger(MembershipRoles.class);
	private static final Duration TTL = Duration.ofSeconds(30);
	private static final Duration VERSION_TTL = Duration.ofDays(1);

	/** KEYS: role, version. ARGV: expected version, role, ttl seconds. */
	private static final DefaultRedisScript<Long> CACHE_IF_UNCHANGED = new DefaultRedisScript<>("""
			local current = redis.call('GET', KEYS[2])
			if not current then current = '0' end
			if current ~= ARGV[1] then return 0 end
			redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
			return 1
			""", Long.class);

	private final WorkspaceService workspaces;
	private final StringRedisTemplate redis;

	MembershipRoles(WorkspaceService workspaces, StringRedisTemplate redis) {
		this.workspaces = workspaces;
		this.redis = redis;
	}

	Optional<Role> roleOf(UUID user, UUID workspace) {
		String cached = redis.opsForValue().get(key(workspace, user));
		if (cached != null) {
			return Role.parse(cached);
		}
		String version = version(workspace, user);
		Optional<Role> role = workspaces.roleOf(user, workspace).flatMap(Role::parse);
		role.ifPresent(r -> cacheIfUnchanged(workspace, user, r, version));
		return role;
	}

	/** 403 unless the caller belongs to the Workspace with at least {@code minimum}. */
	public Role require(UUID user, UUID workspace, Role minimum) {
		Role role = roleOf(user, workspace).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
		if (!role.atLeast(minimum)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		return role;
	}

	String version(UUID workspace, UUID user) {
		String version = redis.opsForValue().get(versionKey(workspace, user));
		return version == null ? "0" : version;
	}

	boolean cacheIfUnchanged(UUID workspace, UUID user, Role role, String expectedVersion) {
		Long cached = redis.execute(CACHE_IF_UNCHANGED, List.of(key(workspace, user), versionKey(workspace, user)),
				expectedVersion, role.name(), String.valueOf(TTL.toSeconds()));
		return cached != null && cached == 1L;
	}

	/**
	 * Called after a change commits. A Redis failure must not turn a committed change into a 500; the
	 * cached entry then expires on its own within the TTL.
	 */
	void forget(UUID workspace, UUID user) {
		try {
			redis.opsForValue().increment(versionKey(workspace, user));
			redis.expire(versionKey(workspace, user), VERSION_TTL);
			redis.delete(key(workspace, user));
		}
		catch (RuntimeException e) {
			log.warn("Could not invalidate cached role for {} in {}; it expires within {}s", user, workspace, TTL.toSeconds(), e);
		}
	}

	private static String key(UUID workspace, UUID user) {
		return "role:" + workspace + ":" + user;
	}

	private static String versionKey(UUID workspace, UUID user) {
		return "rolever:" + workspace + ":" + user;
	}

}
