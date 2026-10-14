package dev.salman.Lowcodebackend.workspace;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Serializes conflicting changes within one Workspace until the surrounding transaction ends. */
final class WorkspaceLock {

	private WorkspaceLock() {
	}

	static void acquire(JdbcClient jdbc, UUID workspace) {
		jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))").param(workspace.toString()).query().singleRow();
	}

}
