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
			case POSTGRESQL -> tlsPostgres();
			case MYSQL -> new MySQLContainer(DockerImageName.parse("mysql:9")).withDatabaseName("tenant")
				.withUsername("tenant").withPassword("tnt-pw-7d41c");
			case ORACLE -> new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
				.withUsername("tenant").withPassword("tnt-pw-7d41c");
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
				container.getUsername(), container.getPassword(), caCertificate(dialect, container));
	}

	private static final String TLS_DIR = "/tls";

	/**
	 * PostgreSQL that serves TLS with a throwaway self-signed certificate, valid for the names the tests connect by
	 * ({@code db.test.internal}, {@code localhost}, 127.0.0.1). The certificate doubles as the CA to trust.
	 */
	private static PostgreSQLContainer tlsPostgres() {
		String script = "mkdir -p " + TLS_DIR + " && cd " + TLS_DIR
				+ " && openssl req -x509 -newkey rsa:2048 -nodes -keyout server.key -out server.crt -days 2"
				+ " -subj /CN=db.test.internal -addext subjectAltName=DNS:db.test.internal,DNS:localhost,IP:127.0.0.1"
				+ " && chown postgres:postgres server.key server.crt && chmod 600 server.key"
				+ " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=" + TLS_DIR + "/server.crt"
				+ " -c ssl_key_file=" + TLS_DIR + "/server.key";
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18")) {
			@Override
			protected void configure() {
				super.configure();
				setCommand("sh", "-c", script);
			}
		}.withDatabaseName("tenant").withUsername("tenant").withPassword("tnt-pw-7d41c");
	}

	private static String caCertificate(Dialect dialect, JdbcDatabaseContainer<?> container) {
		if (dialect != Dialect.POSTGRESQL) {
			return null;
		}
		return container.copyFileFromContainer(TLS_DIR + "/server.crt", in -> new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
	}

	private static TenantDatabase plainPostgres;

	/** A PostgreSQL without TLS, to prove that a server that cannot encrypt is refused. */
	public static synchronized TenantDatabase postgresWithoutTls() {
		if (plainPostgres == null) {
			PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18")).withDatabaseName("tenant")
				.withUsername("tenant").withPassword("tnt-pw-7d41c");
			container.start();
			plainPostgres = new TenantDatabase(Dialect.POSTGRESQL, container.getHost(), container.getMappedPort(5432),
					container.getJdbcUrl(), container.getUsername(), container.getPassword(), null);
		}
		return plainPostgres;
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
