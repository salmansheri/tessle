package dev.salman.Lowcodebackend.datasource;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Structured audit events: who did what to which object. Detail holds names and outcomes, never secrets or row values. */
@Component
public class AuditLog {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	AuditLog(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Joins the caller's transaction, so the event commits or rolls back with the change it describes. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID workspace, UUID actor, String action, String targetType, UUID target, Map<String, ?> detail) {
		jdbc.sql("""
				insert into audit_events (id, workspace_id, actor_id, action, target_type, target_id, detail)
				values (?, ?, ?, ?, ?, ?, ?::jsonb)
				""").params(UUID.randomUUID(), workspace, actor, action, targetType, target, json.writeValueAsString(detail))
				.update();
	}

}
