package dev.salman.Lowcodebackend.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Opens a connection to a Data source and reports a distinct, fixed outcome. Driver messages are never passed
 * on: they can contain hosts, user names or fragments of the failing statement.
 */
@Component
class ConnectionTester {

	/** error is null on success, otherwise one of the codes below. */
	record Result(boolean ok, String error, String message) {

		static Result success() {
			return new Result(true, null, "Connected.");
		}

	}

	static final String UNREACHABLE = "unreachable";
	static final String BAD_CREDENTIALS = "bad_credentials";
	static final String UNKNOWN_DATABASE = "unknown_database";
	static final String FAILED = "failed";

	private static final Logger log = LoggerFactory.getLogger(ConnectionTester.class);
	private static final int TIMEOUT_SECONDS = 5;

	Result test(String type, String host, int port, String database, String username, String password) {
		Properties props = new Properties();
		props.setProperty("user", username);
		props.setProperty("password", password);
		props.setProperty("connectTimeout", String.valueOf(TIMEOUT_SECONDS));
		props.setProperty("loginTimeout", String.valueOf(TIMEOUT_SECONDS));
		props.setProperty("socketTimeout", String.valueOf(TIMEOUT_SECONDS));
		props.setProperty("ApplicationName", "tessle");
		try (Connection c = DriverManager.getConnection(url(type, host, port, database), props)) {
			return c.isValid(TIMEOUT_SECONDS) ? Result.success()
					: new Result(false, FAILED, "The server accepted the login but the connection is not usable.");
		}
		catch (SQLException e) {
			return classify(e);
		}
	}

	private static Result classify(SQLException e) {
		String state = e.getSQLState();
		log.info("Data source connection test failed (sqlstate {})", state);
		if ("28P01".equals(state) || "28000".equals(state)) {
			return new Result(false, BAD_CREDENTIALS, "The database refused the user name or password.");
		}
		if ("3D000".equals(state)) {
			return new Result(false, UNKNOWN_DATABASE, "The server is reachable, but it has no database with that name.");
		}
		if (state != null && state.startsWith("08")) {
			return new Result(false, UNREACHABLE, "Could not reach the server. Check the host and port.");
		}
		return new Result(false, FAILED, "The connection could not be established.");
	}

	/** Structured fields only (ADR 0006): the URL is built here, from validated parts. */
	static String url(String type, String host, int port, String database) {
		if (!"postgresql".equals(type)) {
			throw new IllegalArgumentException("unsupported type " + type);
		}
		return "jdbc:postgresql://" + host + ":" + port + "/" + java.net.URLEncoder.encode(database, java.nio.charset.StandardCharsets.UTF_8);
	}

}
