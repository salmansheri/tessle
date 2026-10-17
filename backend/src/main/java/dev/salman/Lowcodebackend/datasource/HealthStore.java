package dev.salman.Lowcodebackend.datasource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The background ping's two statements. The ping acts for no user and no Workspace, so it cannot run in a
 * tenant-scoped transaction (ADR 0001: those see nothing without a caller). It uses the privileged connection
 * directly, in autocommit, and only for these statements; nothing else may follow this example.
 */
@Repository
class HealthStore {

	private final JdbcClient jdbc;

	HealthStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<DataSourceRecord> enabled() {
		return jdbc.sql("select " + DataSourceStore.COLUMNS + " from data_sources where state = 'enabled'")
				.query(DataSourceStore::map).list();
	}

	/**
	 * Records a ping only if the row is still enabled and unchanged since it was read, so an edit or a disable made
	 * while the ping ran is never overwritten. Does not touch updated_at.
	 */
	boolean record(DataSourceRecord ds, boolean ok, String error) {
		return jdbc.sql("""
				update data_sources set ping_ok = ?, ping_at = now(), ping_error = ?
				where workspace_id = ? and id = ? and state = 'enabled' and updated_at = ?
				""").params(ok, ok ? null : error, ds.workspaceId(), ds.id(), Timestamp.from(ds.updatedAt())).update() == 1;
	}

}
