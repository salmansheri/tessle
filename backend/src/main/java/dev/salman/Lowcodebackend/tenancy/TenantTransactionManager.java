package dev.salman.Lowcodebackend.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.regex.Pattern;

import dev.salman.Lowcodebackend.identity.TokenService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Scopes every platform transaction to the caller (ADR 0001). On begin it switches to the RLS-subject
 * role and publishes the authenticated user and active Workspace as transaction-local settings that the
 * row-level security policies read. Without an authenticated user the settings are empty and the policies
 * deny everything.
 *
 * <p>Only work done inside a Spring-managed transaction is covered. Platform data access must go through
 * {@code @Transactional} boundaries (repositories already do); a bare autocommit statement on the
 * privileged connection would bypass RLS.
 */
class TenantTransactionManager extends JpaTransactionManager {

	static final String APP_ROLE = "tessle_app";
	private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

	private final String role;

	TenantTransactionManager(EntityManagerFactory emf, String role) {
		super(emf);
		if (!PLAIN_IDENTIFIER.matcher(role).matches()) {
			throw new IllegalArgumentException("Not a plain role name: " + role);
		}
		this.role = role;
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		super.doBegin(transaction, definition);
		try {
			scopeToTenant();
		}
		catch (RuntimeException | Error e) {
			// super.doBegin succeeded, so its resources are bound and nobody else will release them. An
			// EntityManager with an active transaction would not release its connection when closed, so
			// roll back first (as Spring's own failed-begin path does).
			EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager
					.getResource(obtainEntityManagerFactory());
			if (holder != null && holder.getEntityManager().getTransaction().isActive()) {
				holder.getEntityManager().getTransaction().rollback();
			}
			doCleanupAfterCompletion(transaction);
			throw e;
		}
	}

	private void scopeToTenant() {
		Connection connection = DataSourceUtils.getConnection(getDataSource());
		try {
			String user = "";
			String workspace = "";
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
				user = jwt.getSubject() == null ? "" : jwt.getSubject();
				String claim = jwt.getClaimAsString(TokenService.WORKSPACE_CLAIM);
				workspace = claim == null ? "" : claim;
			}
			try (PreparedStatement settings = connection
					.prepareStatement("select set_config('app.user_id', ?, true), set_config('app.workspace_id', ?, true)")) {
				settings.setString(1, user);
				settings.setString(2, workspace);
				settings.execute();
			}
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET LOCAL ROLE " + role);
			}
		}
		catch (SQLException e) {
			// Never continue on the privileged connection: that would silently skip RLS.
			throw new CannotCreateTransactionException("Could not scope the transaction to the current tenant", e);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, getDataSource());
		}
	}

}
