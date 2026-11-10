package com.blankjava.e2e.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * An HTTP client for calls the suite makes to the stack itself, outside the browser (report header,
 * test data).
 *
 * <p>The stack's TLS certificate is self-signed (infra/README.md). With {@code E2E_TLS_CERT} set to
 * that certificate (PEM), the client trusts it and nothing else; hostname verification stays on (its
 * SAN covers {@code frontend} and {@code localhost}). Without the variable, the JVM's default trust
 * store applies, for a stack with a real certificate.
 */
public final class StackHttp {

    private static HttpClient client;

    private StackHttp() {}

    /**
     * Built on first use, so a misconfiguration fails the first test that needs it with its own
     * message (a static initialiser would hide it behind {@code ExceptionInInitializerError}).
     */
    public static synchronized HttpClient client() {
        if (client == null) {
            client = build();
        }
        return client;
    }

    private static HttpClient build() {
        return Config.tlsCertificate()
                .map(certificate -> HttpClient.newBuilder()
                        .sslContext(trusting(certificate))
                        .build())
                .orElseGet(HttpClient::newHttpClient);
    }

    private static SSLContext trusting(Path certificateFile) {
        if (!Files.isReadable(certificateFile)) {
            throw new IllegalStateException(Config.TLS_CERT_VARIABLE + " points to " + certificateFile
                    + ", which does not exist or cannot be read (e2e/README.md).");
        }
        try (InputStream in = Files.newInputStream(certificateFile)) {
            Collection<? extends Certificate> certificates =
                    CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certificates.isEmpty()) {
                throw new IllegalStateException(
                        Config.TLS_CERT_VARIABLE + ": no certificate found in " + certificateFile);
            }
            KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
            trusted.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trusted.setCertificateEntry("stack-" + index++, certificate);
            }
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(trusted);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trust.getTrustManagers(), null);
            return sslContext;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Could not trust the certificate in " + certificateFile + " (" + Config.TLS_CERT_VARIABLE + ")", e);
        }
    }
}
