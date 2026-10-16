package dev.salman.Lowcodebackend.datasource;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator settings for Data sources (ADR 0007). {@code allowedNetworks} lists the private ranges (CIDR) that
 * Data sources may reach; empty by default, so only public addresses are reachable.
 */
@ConfigurationProperties("tessle.datasource")
public record DataSourceProperties(List<String> allowedNetworks) {

	public DataSourceProperties {
		allowedNetworks = allowedNetworks == null ? List.of() : allowedNetworks.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

}
