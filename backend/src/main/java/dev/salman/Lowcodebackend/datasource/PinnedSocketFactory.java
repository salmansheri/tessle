package dev.salman.Lowcodebackend.datasource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.SocketFactory;

/**
 * Creates sockets that always connect to the address the network policy approved, whatever name or address the
 * driver asks for (ADR 0007). Instantiated by the JDBC driver by class name with the registry token.
 */
public class PinnedSocketFactory extends SocketFactory {

	private final InetAddress pinned;

	public PinnedSocketFactory(String token) {
		this.pinned = TenantConnections.lookup(token).address();
	}

	@Override
	public Socket createSocket() {
		return new Socket() {
			@Override
			public void connect(SocketAddress endpoint, int timeout) throws IOException {
				int port = endpoint instanceof InetSocketAddress isa ? isa.getPort() : 0;
				super.connect(new InetSocketAddress(pinned, port), timeout);
			}
		};
	}

	// Every overload ignores the host it is given and dials the pinned address directly: no lookup, forward or reverse.
	@Override
	public Socket createSocket(String host, int port) throws IOException {
		return connected(port, null, 0);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
		return connected(port, localAddress, localPort);
	}

	@Override
	public Socket createSocket(InetAddress address, int port) throws IOException {
		return connected(port, null, 0);
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return connected(port, localAddress, localPort);
	}

	private Socket connected(int port, InetAddress localAddress, int localPort) throws IOException {
		Socket socket = createSocket();
		if (localAddress != null) {
			socket.bind(new InetSocketAddress(localAddress, localPort));
		}
		socket.connect(new InetSocketAddress(pinned, port));
		return socket;
	}

}
