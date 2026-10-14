package dev.salman.Lowcodebackend.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class TenancyConfig {

	/** Replaces Boot's default JPA transaction manager. */
	@Bean
	PlatformTransactionManager transactionManager(EntityManagerFactory emf,
			ObjectProvider<TransactionManagerCustomizers> customizers) {
		TenantTransactionManager manager = new TenantTransactionManager(emf, TenantTransactionManager.APP_ROLE);
		customizers.ifAvailable(c -> c.customize(manager));
		return manager;
	}

}
