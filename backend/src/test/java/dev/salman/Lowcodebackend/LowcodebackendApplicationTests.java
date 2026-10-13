package dev.salman.Lowcodebackend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LowcodebackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void bootsAgainstRealInfrastructureWithBaselineMigrationApplied() {
		Integer rows = jdbc.queryForObject("select count(*) from platform_info", Integer.class);
		assertThat(rows).isEqualTo(1);
	}

}
