package dev.salman.Lowcodebackend.identity;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Test helper: creates users and starts sessions straight from the token service, skipping the email round trip. */
@Component
public class TestSessions {

	/** The cookies a browser holds after signing in. */
	public record Browser(UUID userId, Cookie access, Cookie refresh) {
	}

	@Autowired
	TokenService tokens;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	public Browser signIn(String email) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, email) values (?, ?)", id, email);
		Cookie access = null;
		Cookie refresh = null;
		for (ResponseCookie c : tokens.start(new User(id, email))) {
			if (c.getName().equals(TokenService.ACCESS_COOKIE)) {
				access = new Cookie(c.getName(), c.getValue());
			}
			else if (c.getName().equals(TokenService.REFRESH_COOKIE)) {
				refresh = new Cookie(c.getName(), c.getValue());
			}
		}
		return new Browser(id, access, refresh);
	}

	public void flushRedis() {
		redis.execute((RedisCallback<Object>) c -> {
			c.serverCommands().flushAll();
			return null;
		});
	}

}
