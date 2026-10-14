package dev.salman.Lowcodebackend.system;

import java.sql.SQLException;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Row-level security, unique indexes and constraint triggers are the backstop behind the application's own
 * checks. If one of them fires anyway, answer with the matching HTTP status instead of a 500. Constraint
 * triggers are deferred, so they can also surface when the transaction commits.
 */
@RestControllerAdvice
class DatabaseRefusals {

	private static final String INSUFFICIENT_PRIVILEGE = "42501";
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String CHECK_VIOLATION = "23514"; // e.g. a workspace would lose its last owner

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<Void> onDataAccess(DataAccessException e) {
		return respond(e);
	}

	@ExceptionHandler(TransactionException.class)
	ResponseEntity<Void> onTransaction(TransactionException e) {
		return respond(e);
	}

	private ResponseEntity<Void> respond(RuntimeException e) {
		return statusFor(e).map(status -> ResponseEntity.status(status).<Void>build()).orElseThrow(() -> e);
	}

	private static Optional<HttpStatus> statusFor(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getSQLState() != null) {
				switch (sql.getSQLState()) {
					// 42501 is also a plain missing GRANT, which is a server fault, not a policy refusal.
					case INSUFFICIENT_PRIVILEGE -> {
						if (sql.getMessage() != null && sql.getMessage().contains("row-level security")) {
							return Optional.of(HttpStatus.FORBIDDEN);
						}
					}
					case UNIQUE_VIOLATION, CHECK_VIOLATION -> {
						return Optional.of(HttpStatus.CONFLICT);
					}
					default -> {
					}
				}
			}
		}
		return Optional.empty();
	}

}
