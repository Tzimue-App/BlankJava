package com.blankjava.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/** RSA key pairs generated once per test JVM (generation is slow); never written to the repository. */
final class TestKeys {

    static final KeyPair ACTIVE = generate(JwtKeySet.MIN_KEY_BITS);
    static final KeyPair PREVIOUS = generate(JwtKeySet.MIN_KEY_BITS);
    static final KeyPair OTHER = generate(JwtKeySet.MIN_KEY_BITS);
    static final KeyPair TOO_SMALL = generate(2048);

    private TestKeys() {}

    static RSAPrivateCrtKey privateKey(KeyPair pair) {
        return (RSAPrivateCrtKey) pair.getPrivate();
    }

    static RSAPublicKey publicKey(KeyPair pair) {
        return (RSAPublicKey) pair.getPublic();
    }

    /** PKCS#8 PEM, as written by {@code openssl genpkey}. */
    static String privatePem(KeyPair pair) {
        return pem("PRIVATE KEY", pair.getPrivate());
    }

    /** X.509 PEM, as written by {@code openssl pkey -pubout}. */
    static String publicPem(KeyPair pair) {
        return pem("PUBLIC KEY", pair.getPublic());
    }

    private static String pem(String label, Key key) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded());
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static KeyPair generate(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
