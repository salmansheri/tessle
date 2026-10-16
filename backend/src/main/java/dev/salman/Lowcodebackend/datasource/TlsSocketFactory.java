package dev.salman.Lowcodebackend.datasource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

/** Hands the driver the TLS settings of one Data source (trust, verification). Instantiated by class name. */
public class TlsSocketFactory extends SSLSocketFactory {

	private final SSLSocketFactory delegate;

	public TlsSocketFactory(String token) {
		this.delegate = TenantConnections.lookup(token).tls().getSocketFactory();
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return delegate.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return delegate.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
		return delegate.createSocket(socket, host, port, autoClose);
	}

	@Override
	public Socket createSocket() throws IOException {
		return delegate.createSocket();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		return delegate.createSocket(host, port);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
		return delegate.createSocket(host, port, localHost, localPort);
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return delegate.createSocket(host, port);
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		return delegate.createSocket(address, port, localAddress, localPort);
	}

}
