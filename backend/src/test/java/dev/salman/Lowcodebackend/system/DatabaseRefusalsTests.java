package dev.salman.Lowcodebackend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.TransactionSystemException;

class DatabaseRefusalsTests {

	final DatabaseRefusals handler = new DatabaseRefusals();

	@Test
	void rowLevelSecurityViolationsAreForbidden() {
		var e = new DataIntegrityViolationException("x",
				new SQLException("new row violates row-level security policy for table \"memberships\"", "42501"));
		assertThat(handler.onDataAccess(e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void aMissingGrantIsAServerErrorNotAPolicyRefusal() {
		var e = new UncategorizedSQLException("x", "sql", new SQLException("permission denied for table users", "42501"));
		assertThatThrownBy(() -> handler.onDataAccess(e)).isSameAs(e);
	}

	@Test
	void uniqueAndCheckViolationsAreConflicts() {
		assertThat(handler.onDataAccess(new DuplicateKeyException("x", new SQLException("dup", "23505"))).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
		assertThat(handler.onDataAccess(new DataIntegrityViolationException("x", new SQLException("no owner", "23514"))).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void failuresRaisedAtCommitAreMappedToo() {
		var e = new TransactionSystemException("commit failed", new SQLException("no owner", "23514"));
		assertThat(handler.onTransaction(e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

}
