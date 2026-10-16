package dev.salman.Lowcodebackend.datasource;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Name resolution as a seam, so the network policy can be tested against any DNS answer. */
@FunctionalInterface
interface HostResolver {

	InetAddress[] resolve(String host) throws UnknownHostException;

	/** The system resolver. */
	HostResolver SYSTEM = InetAddress::getAllByName;

}
