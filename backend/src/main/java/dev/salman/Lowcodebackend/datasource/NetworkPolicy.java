package dev.salman.Lowcodebackend.datasource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decides which addresses a Data source may reach (ADR 0007). Loopback, link-local, multicast, unspecified and
 * cloud-metadata addresses are refused whatever the configuration says. Other non-public ranges (private,
 * carrier-grade NAT, unique-local, reserved) are refused unless the operator lists them. The host name is resolved
 * once; the caller connects to the returned address, so a changing DNS answer cannot slip past the check.
 */
@Component
public class NetworkPolicy {

	/** The address is not allowed. The message is safe to show to an admin. */
	public static class Refused extends RuntimeException {

		Refused(String message) {
			super(message);
		}

	}

	/** The name does not resolve. */
	public static class NotFound extends RuntimeException {

		NotFound(String host, Throwable cause) {
			super("Could not resolve " + host, cause);
		}

	}

	/** Cloud instance-metadata endpoints that sit outside link-local ranges. */
	private static final List<InetAddress> METADATA = Stream.of("100.100.100.200", "fd00:ec2::254").map(NetworkPolicy::literal).toList();

	/** Not routable on the public internet. Reachable only when the operator lists the range. */
	private static final List<Cidr> NOT_PUBLIC = Stream.of("0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "172.16.0.0/12",
			"192.0.0.0/24", "192.0.2.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
			"240.0.0.0/4", "fc00::/7", "fec0::/10", "2001:db8::/32",
			// IPv6 forms that carry an IPv4 address, which would otherwise smuggle in a private one.
			"::/96", "64:ff9b::/96", "64:ff9b:1::/48", "2002::/16").map(Cidr::parse).toList();

	private final List<Cidr> allowed;
	private final HostResolver resolver;

	@Autowired
	NetworkPolicy(DataSourceProperties properties) {
		this(properties, HostResolver.SYSTEM);
	}

	NetworkPolicy(DataSourceProperties properties, HostResolver resolver) {
		this.allowed = properties.allowedNetworks().stream().map(Cidr::parse).toList();
		this.resolver = resolver;
	}

	/** Resolves {@code host} once and returns the address to connect to; throws if any answer is not allowed. */
	public InetAddress resolve(String host) {
		InetAddress[] answers;
		try {
			answers = resolver.resolve(host);
		}
		catch (UnknownHostException e) {
			throw new NotFound(host, e);
		}
		if (answers.length == 0) {
			throw new NotFound(host, null);
		}
		// Every answer must pass: the driver could otherwise be handed the one that does not.
		for (InetAddress answer : answers) {
			check(answer);
		}
		// Several answers: prefer IPv4 (the more commonly reachable family from a server), else the first.
		for (InetAddress answer : answers) {
			if (answer.getAddress().length == 4) {
				return answer;
			}
		}
		return answers[0];
	}

	private void check(InetAddress a) {
		if (forbidden(a)) {
			throw new Refused("Connections to this address are never allowed.");
		}
		if (!isPublic(a) && allowed.stream().noneMatch(c -> c.contains(a))) {
			throw new Refused("This address is on a private network the platform operator has not allowed.");
		}
	}

	/** Never reachable, whatever the operator configures. Overridable only so tests can reach a local container. */
	boolean forbidden(InetAddress a) {
		return a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()
				|| METADATA.contains(a);
	}

	private static boolean isPublic(InetAddress a) {
		return NOT_PUBLIC.stream().noneMatch(c -> c.contains(a));
	}

	private static InetAddress literal(String address) {
		try {
			return InetAddress.getByName(address);
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException(e);
		}
	}

}
