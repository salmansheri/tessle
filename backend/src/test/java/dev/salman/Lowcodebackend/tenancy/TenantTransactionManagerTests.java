package dev.salman.Lowcodebackend.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import dev.salman.Lowcodebackend.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantTransactionManagerTests {

	@Autowired
	EntityManagerFactory emf;

	@Autowired
	DataSource dataSource;

	@MockitoBean
	JavaMailSender mail;

	@Test
	void whenTenantScopingFailsTheConnectionAndEntityManagerAreReleased() throws Exception {
		TransactionTemplate failing = new TransactionTemplate(new TenantTransactionManager(emf, "no_such_role"));

		for (int i = 0; i < 15; i++) {
			assertThatThrownBy(() -> failing.executeWithoutResult(status -> {
			})).isInstanceOf(TransactionException.class);
		}

		HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
		assertThat(pool.getHikariPoolMXBean().getActiveConnections()).as("no leaked connections").isZero();
		new TransactionTemplate(new TenantTransactionManager(emf, "tessle_app")).executeWithoutResult(status -> {
		});
	}

	@Test
	void roleNamesThatAreNotPlainIdentifiersAreRejected() {
		assertThatThrownBy(() -> new TenantTransactionManager(emf, "x; drop table users")).isInstanceOf(IllegalArgumentException.class);
	}

}
