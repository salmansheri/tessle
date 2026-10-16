package dev.salman.Lowcodebackend.tenantdb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Hands tests a connected tenant database. PostgreSQL is always available; the others are heavy images
 * (Oracle and SQL Server are over a gigabyte) so they start only when asked for with
 * {@code -Dtessle.tenantdbs=mysql,oracle,sqlserver} (or {@code all}). One container per dialect per JVM.
 */
public final class TenantDatabases {

	public static final String PROPERTY = "tessle.tenantdbs";

	private static final Map<Dialect, TenantDatabase> STARTED = new EnumMap<>(Dialect.class);

	private TenantDatabases() {
	}

	public static boolean isEnabled(Dialect dialect) {
		if (dialect == Dialect.POSTGRESQL) {
			return true;
		}
		String requested = System.getProperty(PROPERTY, "").toLowerCase(Locale.ROOT).trim();
		Set<String> names = Arrays.stream(requested.split(",")).map(String::trim).collect(Collectors.toSet());
		return names.contains("all") || names.contains(dialect.name().toLowerCase(Locale.ROOT));
	}

	/** The database for {@code dialect}, started and seeded on first use. Fails if the dialect was not requested. */
	public static synchronized TenantDatabase get(Dialect dialect) {
		if (!isEnabled(dialect)) {
			throw new IllegalStateException(dialect + " tenant database not requested; pass -D" + PROPERTY + "=" + dialect.name().toLowerCase(Locale.ROOT));
		}
		return STARTED.computeIfAbsent(dialect, TenantDatabases::start);
	}

	public static TenantDatabase postgres() {
		return get(Dialect.POSTGRESQL);
	}

	private static TenantDatabase start(Dialect dialect) {
		JdbcDatabaseContainer<?> container = switch (dialect) {
			case POSTGRESQL -> new PostgreSQLContainer(DockerImageName.parse("postgres:18")).withDatabaseName("tenant")
				.withUsername("tenant").withPassword("tenant");
			case MYSQL -> new MySQLContainer(DockerImageName.parse("mysql:9")).withDatabaseName("tenant")
				.withUsername("tenant").withPassword("tenant");
			case ORACLE -> new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
				.withUsername("tenant").withPassword("tenant");
			case SQLSERVER -> new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
				.acceptLicense();
		};
		container.start();
		try (Connection admin = container.createConnection("")) {
			seed(dialect, admin);
		}
		catch (SQLException e) {
			container.stop();
			throw new IllegalStateException("could not seed the " + dialect + " tenant database", e);
		}
		// SQL Server creates the tenant database in its seed script, so the URL has to name it afterwards.
		String url = dialect == Dialect.SQLSERVER ? container.getJdbcUrl() + ";databaseName=tenant" : container.getJdbcUrl();
		return new TenantDatabase(dialect, container.getHost(), container.getMappedPort(portOf(dialect)), url,
				container.getUsername(), container.getPassword());
	}

	private static int portOf(Dialect dialect) {
		return switch (dialect) {
			case POSTGRESQL -> 5432;
			case MYSQL -> 3306;
			case ORACLE -> 1521;
			case SQLSERVER -> 1433;
		};
	}

	private static void seed(Dialect dialect, Connection connection) throws SQLException {
		try {
			String script = Files.readString(Path.of(System.getProperty("basedir", "."), "tenant-dbs", dialect.seedFile()));
			if (dialect == Dialect.SQLSERVER) {
				// T-SQL batches are separated by a GO line, which is a client command and not part of the language.
				for (String batch : script.split("(?m)^GO\\s*$")) {
					if (!batch.isBlank()) {
						try (var statement = connection.createStatement()) {
							statement.execute(batch);
						}
					}
				}
				return;
			}
			ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8))),
					false, false, "--", ";", "/*", "*/");
		}
		catch (java.io.IOException e) {
			throw new IllegalStateException("seed script for " + dialect + " not found (tests run from backend/)", e);
		}
	}

}
