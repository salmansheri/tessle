package dev.salman.Lowcodebackend.tenantdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** A running tenant test database holding the shared sample schema (customers, orders, invoices). */
public record TenantDatabase(Dialect dialect, String host, int port, String jdbcUrl, String username, String password) {

	public Connection connect() throws SQLException {
		return DriverManager.getConnection(jdbcUrl, username, password);
	}

}
