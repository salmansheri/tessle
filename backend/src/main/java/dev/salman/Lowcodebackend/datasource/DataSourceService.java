package dev.salman.Lowcodebackend.datasource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.workspace.MembershipRoles;
import dev.salman.Lowcodebackend.workspace.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Data source use cases. Roles are checked first (owner and admin manage; builder lists without connection
 * details; editor and viewer are refused); the store's row-level security is the backstop.
 */
@Service
class DataSourceService {

	/** What a caller may see: builders get no connection details. */
	record View(DataSourceRecord record, boolean details) {
	}

	/** verifyCertificate null means the default (on); caCertificate is PEM text or null. */
	record NewDataSource(String logicalName, String label, String type, String host, int port, String database,
			String username, String password, Boolean verifyCertificate, String caCertificate) {
	}

	/** Fields left null are unchanged. The logical name and type are not editable. */
	record Edit(String label, String host, Integer port, String database, String username, String password,
			Boolean verifyCertificate, String caCertificate) {
	}

	private final DataSourceStore store;
	private final MembershipRoles roles;
	private final SecretBox secrets;
	private final ConnectionTester tester;
	private final NetworkPolicy policy;

	DataSourceService(DataSourceStore store, MembershipRoles roles, SecretBox secrets, ConnectionTester tester,
			NetworkPolicy policy) {
		this.policy = policy;
		this.store = store;
		this.roles = roles;
		this.secrets = secrets;
		this.tester = tester;
	}

	View create(UUID user, UUID workspace, NewDataSource n) {
		roles.require(user, workspace, Role.admin);
		UUID id = UUID.randomUUID();
		SecretBox.Sealed password = secrets.seal(n.password(), context(workspace, id));
		SecretBox.Sealed ca = sealCa(n.caCertificate(), workspace, id);
		DataSourceRecord created = store.create(user, new DataSourceRecord(id, workspace, n.logicalName(), n.label().trim(),
				n.type(), n.host(), n.port(), n.database(), n.username(), password.ciphertext(), password.keyId(),
				n.verifyCertificate() == null || n.verifyCertificate(), ca == null ? null : ca.ciphertext(),
				ca == null ? null : ca.keyId(), "disabled", null, null, null, null, null, null, null, null));
		return new View(created, true);
	}

	List<View> list(UUID user, UUID workspace) {
		Role role = roles.require(user, workspace, Role.builder);
		boolean details = role.atLeast(Role.admin);
		return store.list(workspace).stream().map(r -> new View(r, details)).toList();
	}

	View update(UUID user, UUID workspace, UUID id, Edit e) {
		roles.require(user, workspace, Role.admin);
		DataSourceRecord ds = store.find(workspace, id).orElseThrow(DataSourceService::notFound);
		List<String> changed = new ArrayList<>();
		String label = ds.label();
		if (e.label() != null && !e.label().trim().equals(ds.label())) {
			label = e.label().trim();
			changed.add("label");
		}
		String host = pick(e.host(), ds.host(), "host", changed);
		int port = e.port() != null && e.port() != ds.port() ? mark(e.port(), "port", changed) : ds.port();
		String database = pick(e.database(), ds.database(), "database", changed);
		String username = pick(e.username(), ds.username(), "username", changed);
		byte[] ciphertext = ds.passwordCiphertext();
		String keyId = ds.secretKeyId();
		if (e.password() != null) {
			SecretBox.Sealed sealed = secrets.seal(e.password(), context(workspace, id));
			ciphertext = sealed.ciphertext();
			keyId = sealed.keyId();
			changed.add("password");
		}
		boolean tlsVerify = ds.tlsVerify();
		if (e.verifyCertificate() != null && e.verifyCertificate() != ds.tlsVerify()) {
			tlsVerify = e.verifyCertificate();
			changed.add("verifyCertificate");
		}
		byte[] caCiphertext = ds.caCiphertext();
		String caKeyId = ds.caKeyId();
		if (e.caCertificate() != null && caDiffers(ds, e.caCertificate(), workspace, id)) {
			SecretBox.Sealed ca = sealCa(e.caCertificate(), workspace, id); // empty text removes it
			caCiphertext = ca == null ? null : ca.ciphertext();
			caKeyId = ca == null ? null : ca.keyId();
			changed.add("caCertificate");
		}
		boolean connection = changed.stream().anyMatch(c -> !c.equals("label"));
		if (changed.isEmpty()) {
			return new View(ds, true);
		}
		DataSourceRecord next = new DataSourceRecord(id, workspace, ds.logicalName(), label, ds.type(), host, port, database,
				username, ciphertext, keyId, tlsVerify, caCiphertext, caKeyId, ds.state(), ds.lastTestOk(), ds.lastTestAt(), ds.lastTestError(), ds.pingOk(), ds.pingAt(), ds.pingError(), ds.createdAt(), ds.updatedAt());
		return new View(store.update(user, next, connection, changed, ds.tlsVerify() && !tlsVerify).orElseThrow(DataSourceService::changedMeanwhile), true);
	}

	private static String pick(String proposed, String current, String field, List<String> changed) {
		if (proposed == null || proposed.equals(current)) {
			return current;
		}
		changed.add(field);
		return proposed;
	}

	private static int mark(int value, String field, List<String> changed) {
		changed.add(field);
		return value;
	}

	/** The connection is opened outside any transaction: a slow database must not hold a platform connection. */
	ConnectionTester.Result test(UUID user, UUID workspace, UUID id) {
		roles.require(user, workspace, Role.admin);
		DataSourceRecord ds = store.find(workspace, id).orElseThrow(DataSourceService::notFound);
		ConnectionTester.Result result = probe(ds);
		if (!store.recordTest(workspace, user, id, ds.updatedAt(), result.ok(), result.error(), result.message())) {
			throw changedMeanwhile();
		}
		return result;
	}

	/** Dials the Data source with its stored settings, whoever asks (an admin's test or the background ping). */
	ConnectionTester.Result probe(DataSourceRecord ds) {
		UUID workspace = ds.workspaceId();
		String password = secrets.open(ds.passwordCiphertext(), ds.secretKeyId(), context(workspace, ds.id()));
		String ca = ds.caCiphertext() == null ? null : secrets.open(ds.caCiphertext(), ds.caKeyId(), caContext(workspace, ds.id()));
		return tester.test(new ConnectionTester.Target(ds.type(), ds.host(), ds.port(), ds.database(),
				ds.username(), password, ds.tlsVerify(), ca));
	}

	View enable(UUID user, UUID workspace, UUID id) {
		roles.require(user, workspace, Role.admin);
		DataSourceRecord ds = store.find(workspace, id).orElseThrow(DataSourceService::notFound);
		// The address is checked again at enablement: the network policy may have changed since the last test.
		try {
			policy.resolve(ds.host());
		}
		catch (NetworkPolicy.Refused | NetworkPolicy.NotFound refused) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That address is not currently allowed");
		}
		if (!store.enable(workspace, user, id, ds.updatedAt())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A connection test must pass before enabling");
		}
		return new View(store.find(workspace, ds.id()).orElseThrow(), true);
	}

	/** Idempotent: disabling a source that is already off changes nothing. */
	View disable(UUID user, UUID workspace, UUID id) {
		roles.require(user, workspace, Role.admin);
		DataSourceRecord ds = store.find(workspace, id).orElseThrow(DataSourceService::notFound);
		if (ds.enabled() && !store.disable(workspace, user, id, ds.updatedAt())) {
			throw changedMeanwhile();
		}
		return new View(store.find(workspace, id).orElseThrow(DataSourceService::notFound), true);
	}

	void delete(UUID user, UUID workspace, UUID id) {
		roles.require(user, workspace, Role.admin);
		DataSourceRecord ds = store.find(workspace, id).orElseThrow(DataSourceService::notFound);
		try {
			if (!store.delete(workspace, user, id, ds.logicalName())) {
				throw notFound();
			}
		}
		catch (DataIntegrityViolationException inUse) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Something still uses this Data source; remove those first");
		}
	}

	private static ResponseStatusException changedMeanwhile() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "The Data source was changed by someone else; reload and try again");
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	/** Seals CA certificate text; blank means none. Anything that is not a PEM certificate is a 400. */
	private SecretBox.Sealed sealCa(String pem, UUID workspace, UUID id) {
		if (pem == null || pem.isBlank()) {
			return null;
		}
		try {
			TenantConnections.parseCertificates(pem);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The CA certificate must be PEM encoded");
		}
		return secrets.seal(pem, caContext(workspace, id));
	}

	/** True unless the proposed CA is what is already stored (or both are absent), so a no-op edit changes nothing. */
	private boolean caDiffers(DataSourceRecord ds, String proposed, UUID workspace, UUID id) {
		if (proposed.isBlank()) {
			return ds.caCiphertext() != null;
		}
		return ds.caCiphertext() == null
				|| !secrets.open(ds.caCiphertext(), ds.caKeyId(), caContext(workspace, id)).trim().equals(proposed.trim());
	}

	static String caContext(UUID workspace, UUID id) {
		return "data-source:" + workspace + ":" + id + ":ca";
	}

	/** Binds a sealed secret to its row, so it cannot be moved to another Data source or Workspace. */
	static String context(UUID workspace, UUID id) {
		return "data-source:" + workspace + ":" + id + ":password";
	}

}
