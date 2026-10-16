package dev.salman.Lowcodebackend.tenantdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Proves every tenant test database serves the same seeded rows. Non-PostgreSQL ones run only when requested. */
class TenantDatabasesTests {

	@ParameterizedTest
	@EnumSource(Dialect.class)
	void seededRowsAreReadable(Dialect dialect) throws Exception {
		assumeTrue(TenantDatabases.isEnabled(dialect), dialect + " not requested (-D" + TenantDatabases.PROPERTY + ")");
		try (Connection c = TenantDatabases.get(dialect).connect(); Statement s = c.createStatement()) {
			assertThat(count(s, "customers")).isEqualTo(3);
			assertThat(count(s, "orders")).isEqualTo(4);
			assertThat(count(s, "invoices")).isEqualTo(3);
			try (ResultSet r = s.executeQuery("SELECT c.name FROM customers c JOIN orders o ON o.customer_id = c.id WHERE o.id = 3")) {
				assertThat(r.next()).isTrue();
				assertThat(r.getString(1)).isEqualTo("Grace Hopper");
			}
		}
	}

	private static int count(Statement s, String table) throws Exception {
		try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
			r.next();
			return r.getInt(1);
		}
	}

}
