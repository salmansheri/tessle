package dev.salman.Lowcodebackend.datasource;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Lets API tests reach the Testcontainers databases, which sit on loopback and are therefore refused by the real
 * policy. Only loopback is relaxed; every other rule still applies. The names below stand in for DNS, so tests can
 * prove the driver connects to the address resolved once instead of looking the name up again.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestNetwork {

	/** Test host names and what they "resolve" to: both point at the local container. */
	public static final String RIGHT_NAME = "db.test.internal";
	public static final String WRONG_NAME = "wrong.test.internal";

	@Bean
	@Primary
	NetworkPolicy loopbackFriendlyPolicy() {
		HostResolver dns = host -> {
			if (host.equals(RIGHT_NAME) || host.equals(WRONG_NAME)) {
				return new InetAddress[] { InetAddress.getByName("127.0.0.1") };
			}
			return InetAddress.getAllByName(host);
		};
		return new NetworkPolicy(new DataSourceProperties(List.of("127.0.0.0/8", "::1/128")), dns) {
			@Override
			boolean forbidden(InetAddress a) {
				return !a.isLoopbackAddress() && super.forbidden(a);
			}
		};
	}

}
