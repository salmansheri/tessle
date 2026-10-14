package dev.salman.Lowcodebackend.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MembershipRolesTests {

	@Autowired
	MembershipRoles roles;

	@Autowired
	StringRedisTemplate redis;

	@MockitoBean
	JavaMailSender mail;

	@Test
	void aRoleReadBeforeAnInvalidationIsNeverCachedAfterIt() {
		UUID ws = UUID.randomUUID();
		UUID user = UUID.randomUUID();
		String versionBeforeRead = roles.version(ws, user);

		roles.forget(ws, user); // a demotion commits while a slow request still holds the old role

		assertThat(roles.cacheIfUnchanged(ws, user, Role.admin, versionBeforeRead)).isFalse();
		assertThat(redis.hasKey("role:" + ws + ":" + user)).isFalse();
		assertThat(roles.cacheIfUnchanged(ws, user, Role.viewer, roles.version(ws, user))).isTrue();
		assertThat(redis.opsForValue().get("role:" + ws + ":" + user)).isEqualTo("viewer");
	}

}
