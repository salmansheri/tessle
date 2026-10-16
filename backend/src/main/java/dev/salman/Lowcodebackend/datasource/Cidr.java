package dev.salman.Lowcodebackend.datasource;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** A network such as {@code 10.0.0.0/8} or {@code fd12:3456::/32}. Addresses of the other IP family never match. */
record Cidr(byte[] network, int prefix) {

	static Cidr parse(String text) {
		int slash = text.indexOf('/');
		if (slash < 1 || slash == text.length() - 1) {
			throw new IllegalArgumentException("Not a network in CIDR form (address/prefix): " + text);
		}
		String literal = text.substring(0, slash);
		if (!literal.matches("[0-9a-fA-F:.]+")) { // IP literals only: never resolve a name while reading configuration
			throw new IllegalArgumentException("Not a network in CIDR form (address/prefix): " + text);
		}
		try {
			byte[] address = InetAddress.getByName(literal).getAddress();
			int prefix = Integer.parseInt(text.substring(slash + 1));
			if (prefix < 0 || prefix > address.length * 8) {
				throw new IllegalArgumentException("Prefix length out of range: " + text);
			}
			return new Cidr(address, prefix);
		}
		catch (UnknownHostException | NumberFormatException e) {
			throw new IllegalArgumentException("Not a network in CIDR form (address/prefix): " + text, e);
		}
	}

	boolean contains(InetAddress address) {
		byte[] other = address.getAddress();
		if (other.length != network.length) {
			return false;
		}
		int fullBytes = prefix / 8;
		for (int i = 0; i < fullBytes; i++) {
			if (network[i] != other[i]) {
				return false;
			}
		}
		int rest = prefix % 8;
		if (rest == 0) {
			return true;
		}
		int mask = 0xFF << (8 - rest) & 0xFF;
		return (network[fullBytes] & mask) == (other[fullBytes] & mask);
	}

}
