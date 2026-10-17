package dev.salman.Lowcodebackend.datasource;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A stored Data source, including its sealed password. Never leaves the module; responses use views. */
record DataSourceRecord(UUID id, UUID workspaceId, String logicalName, String label, String type, String host, int port,
		String database, String username, byte[] passwordCiphertext, String secretKeyId, boolean tlsVerify,
		byte[] caCiphertext, String caKeyId, String state,
		Boolean lastTestOk, Instant lastTestAt, String lastTestError, Boolean pingOk, Instant pingAt, String pingError,
		Instant createdAt, Instant updatedAt) {

	boolean enabled() {
		return "enabled".equals(state);
	}

	/** What anything that opens a pool or runs a query must call first, so every caller gives the same message. */
	void requireEnabled() {
		if (!enabled()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This data source is disabled");
		}
	}

}
