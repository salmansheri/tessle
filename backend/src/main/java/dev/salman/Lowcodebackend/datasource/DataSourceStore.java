package dev.salman.Lowcodebackend.datasource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data source rows. Each method that changes something also writes its audit event in the same transaction.
 * Row-level security is the backstop; callers check roles first.
 */
@Repository
class DataSourceStore {

	private static final String COLUMNS = """
			id, workspace_id, logical_name, label, type, host, port, database_name, username, password_ciphertext,
			secret_key_id, tls_verify, ca_ciphertext, ca_key_id, state, last_test_ok, last_test_at, last_test_error, created_at, updated_at
			""";

	private final JdbcClient jdbc;
	private final AuditLog audit;

	DataSourceStore(JdbcClient jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional
	DataSourceRecord create(UUID actor, DataSourceRecord ds) {
		jdbc.sql("""
				insert into data_sources (id, workspace_id, logical_name, label, type, host, port, database_name, username,
				                          password_ciphertext, secret_key_id, tls_verify, ca_ciphertext, ca_key_id, created_by)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""").params(ds.id(), ds.workspaceId(), ds.logicalName(), ds.label(), ds.type(), ds.host(), ds.port(),
				ds.database(), ds.username(), ds.passwordCiphertext(), ds.secretKeyId(), ds.tlsVerify(), ds.caCiphertext(), ds.caKeyId(), actor)
				.update();
		audit.record(ds.workspaceId(), actor, "datasource.created", "data_source", ds.id(),
				Map.of("logicalName", ds.logicalName(), "type", ds.type(), "host", ds.host(), "port", ds.port(),
						"tlsVerify", ds.tlsVerify(), "caCertificate", ds.caCiphertext() != null));
		if (!ds.tlsVerify()) {
			audit.record(ds.workspaceId(), actor, "datasource.tls_verification_disabled", "data_source", ds.id(), Map.of());
		}
		return find(ds.workspaceId(), ds.id()).orElseThrow();
	}

	/**
	 * Records a test result only if the row is exactly as the test read it; an edit made while the test ran would
	 * otherwise be vouched for by a test of the old settings.
	 */
	@Transactional
	boolean recordTest(UUID workspace, UUID actor, UUID id, Instant readAt, boolean ok, String error, String message) {
		int changed = jdbc.sql("""
				update data_sources set last_test_ok = ?, last_test_at = now(), last_test_error = ?, updated_at = now()
				where workspace_id = ? and id = ? and updated_at = ?
				""").params(ok, ok ? null : message, workspace, id, Timestamp.from(readAt)).update();
		if (changed == 1) {
			audit.record(workspace, actor, "datasource.tested", "data_source", id,
					ok ? Map.of("ok", true) : Map.of("ok", false, "error", error));
		}
		return changed == 1;
	}

	/** Enables only when the latest test passed and the row is as it was read; false (and no change) otherwise. */
	@Transactional
	boolean enable(UUID workspace, UUID actor, UUID id, Instant readAt) {
		int changed = jdbc.sql("""
				update data_sources set state = 'enabled', updated_at = now()
				where workspace_id = ? and id = ? and last_test_ok is true and updated_at = ?
				""").params(workspace, id, Timestamp.from(readAt)).update();
		if (changed == 1) {
			audit.record(workspace, actor, "datasource.enabled", "data_source", id, Map.of());
		}
		return changed == 1;
	}

	/**
	 * Applies an edit. When a connection field or the password changed, the earlier test no longer vouches for it,
	 * so the result is cleared and the source is switched off until it is tested and enabled again. Empty when the
	 * row changed since {@code next.updatedAt()} was read, so a concurrent edit is never overwritten.
	 */
	@Transactional
	Optional<DataSourceRecord> update(UUID actor, DataSourceRecord next, boolean connectionChanged, List<String> changed,
			boolean verificationNowOff) {
		int rows = jdbc.sql("""
				update data_sources set label = ?, host = ?, port = ?, database_name = ?, username = ?,
				       password_ciphertext = ?, secret_key_id = ?, tls_verify = ?, ca_ciphertext = ?, ca_key_id = ?,
				       updated_at = now(),
				       state = case when ? then 'disabled' else state end,
				       last_test_ok = case when ? then null else last_test_ok end,
				       last_test_at = case when ? then null else last_test_at end,
				       last_test_error = case when ? then null else last_test_error end
				where workspace_id = ? and id = ? and updated_at = ?
				""").params(next.label(), next.host(), next.port(), next.database(), next.username(),
				next.passwordCiphertext(), next.secretKeyId(), next.tlsVerify(), next.caCiphertext(), next.caKeyId(), connectionChanged, connectionChanged, connectionChanged,
				connectionChanged, next.workspaceId(), next.id(), Timestamp.from(next.updatedAt())).update();
		if (rows == 0) {
			return Optional.empty(); // edited or deleted since it was read
		}
		audit.record(next.workspaceId(), actor, "datasource.updated", "data_source", next.id(), Map.of("changed", changed));
		if (verificationNowOff) {
			audit.record(next.workspaceId(), actor, "datasource.tls_verification_disabled", "data_source", next.id(), Map.of());
		}
		return find(next.workspaceId(), next.id());
	}

	@Transactional(readOnly = true)
	List<DataSourceRecord> list(UUID workspace) {
		return jdbc.sql("select " + COLUMNS + " from data_sources where workspace_id = ? order by created_at, logical_name")
				.param(workspace).query(DataSourceStore::map).list();
	}

	@Transactional(readOnly = true)
	Optional<DataSourceRecord> find(UUID workspace, UUID id) {
		return jdbc.sql("select " + COLUMNS + " from data_sources where workspace_id = ? and id = ?")
				.params(workspace, id).query(DataSourceStore::map).optional();
	}

	private static DataSourceRecord map(ResultSet rs, int n) throws SQLException {
		return new DataSourceRecord(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
				rs.getString("logical_name"), rs.getString("label"), rs.getString("type"), rs.getString("host"),
				rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
				rs.getBytes("password_ciphertext"), rs.getString("secret_key_id"), rs.getBoolean("tls_verify"),
				rs.getBytes("ca_ciphertext"), rs.getString("ca_key_id"), rs.getString("state"),
				(Boolean) rs.getObject("last_test_ok"), instant(rs.getTimestamp("last_test_at")),
				rs.getString("last_test_error"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
	}

	private static Instant instant(Timestamp t) {
		return t == null ? null : t.toInstant();
	}

}
