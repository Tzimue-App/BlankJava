package com.blankjava.e2e.support;

import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * An HTTP client for calls the suite makes to the stack itself, outside the browser (report header,
 * test data). The stack's TLS certificate is self-signed (infra/README.md): this client only relaxes
 * trust, for the stack under test and nothing else.
 */
public final class StackHttp {

    private static final HttpClient CLIENT = build();

    private StackHttp() {}

    public static HttpClient client() {
        return CLIENT;
    }

    private static HttpClient build() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {new PermissiveTrustManager()}, new SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Could not build a TLS client", e);
        }
    }

    private static final class PermissiveTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
