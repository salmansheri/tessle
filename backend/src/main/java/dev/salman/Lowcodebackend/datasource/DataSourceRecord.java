package dev.salman.Lowcodebackend.datasource;

import java.time.Instant;
import java.util.UUID;

/** A stored Data source, including its sealed password. Never leaves the module; responses use views. */
record DataSourceRecord(UUID id, UUID workspaceId, String logicalName, String label, String type, String host, int port,
		String database, String username, byte[] passwordCiphertext, String secretKeyId, String state,
		Boolean lastTestOk, Instant lastTestAt, String lastTestError, Instant createdAt, Instant updatedAt) {

	boolean enabled() {
		return "enabled".equals(state);
	}

}
