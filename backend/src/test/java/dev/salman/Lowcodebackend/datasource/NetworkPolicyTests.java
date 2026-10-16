package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The operator-controlled network policy (ADR 0007), exercised as a pure function with a fake resolver. */
class NetworkPolicyTests {

	/** Resolves only the names in the table; literal addresses resolve to themselves, like real DNS does. */
	static HostResolver dns(Map<String, List<String>> table) {
		return host -> {
			List<String> answers = table.get(host);
			if (answers == null) {
				return InetAddress.getAllByName(host); // literals only; unknown names throw UnknownHostException
			}
			InetAddress[] out = new InetAddress[answers.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = InetAddress.getByName(answers.get(i));
			}
			return out;
		};
	}

	static NetworkPolicy policy(Map<String, List<String>> table, String... allowedNetworks) {
		return new NetworkPolicy(new DataSourceProperties(List.of(allowedNetworks)), dns(table));
	}

	@Test
	void loopbackLinkLocalAndCloudMetadataAreAlwaysRefused() {
		NetworkPolicy p = policy(Map.of());
		for (String host : new String[] { "127.0.0.1", "127.8.9.10", "::1", "0.0.0.0", "::", "169.254.169.254",
				"169.254.170.2", "fe80::1", "fd00:ec2::254", "100.100.100.200", "224.0.0.1", "::ffff:127.0.0.1",
				"::ffff:169.254.169.254" }) {
			assertThatThrownBy(() -> p.resolve(host)).as(host).isInstanceOf(NetworkPolicy.Refused.class);
		}
	}

	@Test
	void theOperatorCannotAllowLoopbackOrMetadataEvenByListingThem() {
		NetworkPolicy p = policy(Map.of(), "127.0.0.0/8", "169.254.0.0/16", "0.0.0.0/0", "::/0", "fd00:ec2::/32");
		assertThatThrownBy(() -> p.resolve("127.0.0.1")).isInstanceOf(NetworkPolicy.Refused.class);
		assertThatThrownBy(() -> p.resolve("169.254.169.254")).isInstanceOf(NetworkPolicy.Refused.class);
		assertThatThrownBy(() -> p.resolve("fd00:ec2::254")).isInstanceOf(NetworkPolicy.Refused.class);
	}

	@Test
	void privateRangesAreRefusedUnlessTheOperatorListsThem() throws Exception {
		for (String host : new String[] { "10.1.2.3", "172.16.5.5", "172.31.255.255", "192.168.1.10", "100.64.0.1", "fd12:3456::1", "64:ff9b::a00:1", "2002:a00:1::1", "::a00:1" }) {
			assertThatThrownBy(() -> policy(Map.of()).resolve(host)).as(host).isInstanceOf(NetworkPolicy.Refused.class);
		}
		NetworkPolicy p = policy(Map.of(), "10.0.0.0/8", "fd12:3456::/32");
		assertThat(p.resolve("10.1.2.3")).isEqualTo(InetAddress.getByName("10.1.2.3"));
		assertThat(p.resolve("fd12:3456::1")).isEqualTo(InetAddress.getByName("fd12:3456::1"));
		// Listing one range does not open the others.
		assertThatThrownBy(() -> p.resolve("192.168.1.10")).isInstanceOf(NetworkPolicy.Refused.class);
	}

	@Test
	void publicAddressesAreAllowed() throws Exception {
		assertThat(policy(Map.of()).resolve("93.184.216.34")).isEqualTo(InetAddress.getByName("93.184.216.34"));
	}

	@Test
	void aHostnameIsCheckedByWhatItResolvesTo() throws Exception {
		NetworkPolicy p = policy(Map.of("looks-public.example", List.of("127.0.0.1"), "meta.example", List.of("169.254.169.254"),
				"mixed.example", List.of("93.184.216.34", "10.0.0.5"), "fine.example", List.of("93.184.216.34")));

		assertThatThrownBy(() -> p.resolve("looks-public.example")).isInstanceOf(NetworkPolicy.Refused.class);
		assertThatThrownBy(() -> p.resolve("meta.example")).isInstanceOf(NetworkPolicy.Refused.class);
		// One bad answer is enough: the driver might otherwise pick it.
		assertThatThrownBy(() -> p.resolve("mixed.example")).isInstanceOf(NetworkPolicy.Refused.class);
		assertThat(p.resolve("fine.example")).isEqualTo(InetAddress.getByName("93.184.216.34"));
	}

	@Test
	void theNameIsResolvedExactlyOnceAndTheAnswerIsWhatIsReturned() throws Exception {
		int[] lookups = { 0 };
		HostResolver rebinding = host -> {
			lookups[0]++;
			// A rebinding server answers differently each time; only the first answer may count.
			return new InetAddress[] { InetAddress.getByName(lookups[0] == 1 ? "93.184.216.34" : "127.0.0.1") };
		};
		NetworkPolicy p = new NetworkPolicy(new DataSourceProperties(List.of()), rebinding);

		assertThat(p.resolve("rebind.example")).isEqualTo(InetAddress.getByName("93.184.216.34"));
		assertThat(lookups[0]).isEqualTo(1);
	}

	@Test
	void anUnknownNameIsReportedAsNotFoundRatherThanRefused() {
		assertThatThrownBy(() -> policy(Map.of()).resolve("does-not-exist.invalid")).isInstanceOf(NetworkPolicy.NotFound.class);
	}

	@Test
	void aMalformedNetworkInConfigurationStopsStartup() {
		assertThatThrownBy(() -> policy(Map.of(), "10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> policy(Map.of(), "not-a-network")).isInstanceOf(IllegalArgumentException.class);
		// A name is never looked up while reading configuration.
		assertThatThrownBy(() -> policy(Map.of(), "internal.corp/8")).isInstanceOf(IllegalArgumentException.class);
	}

}
