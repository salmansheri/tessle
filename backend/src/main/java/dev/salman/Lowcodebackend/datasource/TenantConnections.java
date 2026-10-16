package dev.salman.Lowcodebackend.datasource;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * How the platform dials a Data source (ADR 0006, ADR 0007): the JDBC URL is built from structured fields, the
 * socket goes to the address the network policy resolved (the driver never looks the name up again), and TLS is
 * always on. With verification on, the certificate chain and the host name given by the admin are both checked,
 * trusting the system roots plus the Data source's own CA, if any.
 *
 * <p>The driver instantiates its socket and TLS factories by class name, so the per-connection state they need
 * is handed over through a short-lived registry keyed by a random token.
 */
final class TenantConnections implements AutoCloseable {

	record Entry(InetAddress address, SSLContext tls) {
	}

	private static final Map<String, Entry> REGISTRY = new ConcurrentHashMap<>();

	static Entry lookup(String token) {
		Entry entry = REGISTRY.get(token);
		if (entry == null) {
			throw new IllegalStateException("connection settings are no longer registered");
		}
		return entry;
	}

	private final String token = UUID.randomUUID().toString();
	private final String url;
	private final Properties properties = new Properties();

	/**
	 * @param host the name the admin entered; used in the URL so the certificate is checked against it
	 * @param pinned the address the policy approved; the only one ever dialled
	 * @param caPem an extra trusted CA (PEM), or null
	 */
	TenantConnections(String type, String host, int port, String database, String username, String password,
			InetAddress pinned, boolean verify, String caPem, int timeoutSeconds) {
		if (!"postgresql".equals(type)) {
			throw new IllegalArgumentException("unsupported type " + type);
		}
		REGISTRY.put(token, new Entry(pinned, sslContext(verify, caPem)));
		this.url = "jdbc:postgresql://" + host + ":" + port + "/" + java.net.URLEncoder.encode(database, StandardCharsets.UTF_8);
		properties.setProperty("user", username);
		properties.setProperty("password", password);
		properties.setProperty("connectTimeout", String.valueOf(timeoutSeconds));
		properties.setProperty("loginTimeout", String.valueOf(timeoutSeconds));
		properties.setProperty("socketTimeout", String.valueOf(timeoutSeconds));
		properties.setProperty("ApplicationName", "tessle");
		properties.setProperty("socketFactory", PinnedSocketFactory.class.getName());
		properties.setProperty("socketFactoryArg", token);
		properties.setProperty("sslfactory", TlsSocketFactory.class.getName());
		properties.setProperty("sslfactoryarg", token);
		// Encryption is never optional. verify-full also checks the host name against the certificate.
		properties.setProperty("sslmode", verify ? "verify-full" : "require");
	}

	Connection connect() throws SQLException {
		return DriverManager.getConnection(url, properties);
	}

	@Override
	public void close() {
		REGISTRY.remove(token);
	}

	/** Parses PEM text into certificates; throws if there is not at least one valid certificate. */
	static List<X509Certificate> parseCertificates(String pem) {
		try {
			Collection<? extends Certificate> parsed = CertificateFactory.getInstance("X.509")
					.generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
			List<X509Certificate> out = new ArrayList<>();
			for (Certificate c : parsed) {
				out.add((X509Certificate) c);
			}
			if (out.isEmpty()) {
				throw new IllegalArgumentException("no certificate found");
			}
			return out;
		}
		catch (GeneralSecurityException | ClassCastException e) {
			throw new IllegalArgumentException("not a valid PEM certificate", e);
		}
	}

	private static SSLContext sslContext(boolean verify, String caPem) {
		try {
			TrustManager[] trust;
			if (!verify) {
				trust = new TrustManager[] { new AcceptAnyCertificate() };
			}
			else {
				KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
				store.load(null, null);
				int n = 0;
				for (X509Certificate root : systemRoots()) {
					store.setCertificateEntry("system-" + n++, root);
				}
				if (caPem != null) {
					for (X509Certificate ca : parseCertificates(caPem)) {
						store.setCertificateEntry("data-source-" + n++, ca);
					}
				}
				TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				factory.init(store);
				trust = factory.getTrustManagers();
			}
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, trust, null);
			return context;
		}
		catch (GeneralSecurityException | java.io.IOException e) {
			throw new IllegalStateException("could not set up TLS", e);
		}
	}

	private static List<X509Certificate> systemRoots() throws GeneralSecurityException {
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore) null);
		List<X509Certificate> roots = new ArrayList<>();
		for (TrustManager tm : factory.getTrustManagers()) {
			if (tm instanceof X509TrustManager x509) {
				roots.addAll(List.of(x509.getAcceptedIssuers()));
			}
		}
		return roots;
	}

	/** Used only when an admin has knowingly turned certificate verification off (it is audited). Still encrypted. */
	private static final class AcceptAnyCertificate implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

	}

}
