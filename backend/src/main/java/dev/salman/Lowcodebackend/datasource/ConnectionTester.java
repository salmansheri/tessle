package dev.salman.Lowcodebackend.datasource;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Opens a connection to a Data source and reports a distinct, fixed outcome. Driver messages are never passed
 * on: they can contain hosts, user names or fragments of the failing statement. The address is checked by the
 * network policy first, and the connection goes to that one resolved address.
 */
@Component
class ConnectionTester {

	/** error is null on success, otherwise one of the codes below. */
	record Result(boolean ok, String error, String message) {

		static Result success() {
			return new Result(true, null, "Connected.");
		}

	}

	/** What is needed to dial one Data source. The password and CA are plain text here and never stored this way. */
	record Target(String type, String host, int port, String database, String username, String password,
			boolean verifyCertificate, String caCertificatePem) {
	}

	static final String UNREACHABLE = "unreachable";
	static final String BLOCKED_ADDRESS = "blocked_address";
	static final String TLS_FAILED = "tls_failed";
	static final String BAD_CREDENTIALS = "bad_credentials";
	static final String UNKNOWN_DATABASE = "unknown_database";
	static final String FAILED = "failed";

	private static final Logger log = LoggerFactory.getLogger(ConnectionTester.class);
	private static final int TIMEOUT_SECONDS = 5;

	private final NetworkPolicy policy;

	ConnectionTester(NetworkPolicy policy) {
		this.policy = policy;
	}

	Result test(Target t) {
		InetAddress pinned;
		try {
			pinned = policy.resolve(t.host());
		}
		catch (NetworkPolicy.Refused e) {
			return new Result(false, BLOCKED_ADDRESS, "That address is not allowed. Ask your platform operator which networks Tessle may reach.");
		}
		catch (NetworkPolicy.NotFound e) {
			return new Result(false, UNREACHABLE, "Could not find that host. Check the name.");
		}
		try (TenantConnections connections = new TenantConnections(t.type(), t.host(), t.port(), t.database(), t.username(),
				t.password(), pinned, t.verifyCertificate(), t.caCertificatePem(), TIMEOUT_SECONDS);
				Connection c = connections.connect()) {
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
		if (isTlsProblem(e)) {
			return new Result(false, TLS_FAILED, "The connection could not be encrypted or the server's certificate was not trusted. "
					+ "Upload the server's CA certificate, or check that the server supports TLS and that the host name matches its certificate.");
		}
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

	private static boolean isTlsProblem(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLException) {
				return true;
			}
			String message = cause.getMessage();
			if (message != null && (message.contains("does not support SSL") || message.contains("could not be verified"))) {
				return true;
			}
		}
		return false;
	}

}
