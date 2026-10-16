package dev.salman.Lowcodebackend.tenantdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * A running tenant test database holding the shared sample schema. {@code caCertificatePem} is the certificate a
 * TLS-enabled server presents (self-signed, so it is its own CA); null when the server has no TLS.
 */
public record TenantDatabase(Dialect dialect, String host, int port, String jdbcUrl, String username, String password,
		String caCertificatePem) {

	public Connection connect() throws SQLException {
		return DriverManager.getConnection(jdbcUrl, username, password);
	}

}
