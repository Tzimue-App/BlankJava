package com.blankjava.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The RSA keys of the access tokens (docs/technical_analysis.md §5.2): one active key that signs,
 * and the public keys that verify — the active one plus previous ones kept during a rotation. Each
 * key's {@code kid} is its RFC 7638 thumbprint, so it is stable and needs no configuration.
 *
 * <p>Key files are PEM: the active private key in PKCS#8 ({@code BEGIN PRIVATE KEY}, what {@code
 * openssl genpkey} writes), previous public keys in X.509 ({@code BEGIN PUBLIC KEY}).
 */
public final class JwtKeySet {

    /** RSA 3072, docs/technical_analysis.md §5.2. */
    public static final int MIN_KEY_BITS = 3072;

    private final RSAKey signingKey;
    private final List<RSAKey> verificationKeys;

    private JwtKeySet(RSAKey signingKey, List<RSAKey> previousKeys) {
        this.signingKey = signingKey;
        List<RSAKey> keys = new ArrayList<>();
        keys.add(signingKey.toPublicJWK());
        keys.addAll(previousKeys);
        this.verificationKeys = List.copyOf(keys);
    }

    /** Reads the active private key and the previous public keys from PEM files. */
    public static JwtKeySet load(Path privateKeyFile, List<Path> previousPublicKeyFiles) {
        RSAPrivateCrtKey privateKey = readPrivateKey(privateKeyFile);
        List<RSAKey> previous = previousPublicKeyFiles.stream()
                .map(file -> rsaKey(readPublicKey(file), file))
                .toList();
        return new JwtKeySet(signingKey(privateKey, privateKeyFile), previous);
    }

    /** Builds a key set from keys already in memory (tests). */
    public static JwtKeySet of(RSAPrivateCrtKey privateKey, List<RSAPublicKey> previousPublicKeys) {
        Path inMemory = Path.of("(in memory)");
        return new JwtKeySet(
                signingKey(privateKey, inMemory),
                previousPublicKeys.stream().map(key -> rsaKey(key, inMemory)).toList());
    }

    /** A new random key pair, lost on restart: tests and the dev profile only. */
    public static JwtKeySet generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_KEY_BITS);
            return of((RSAPrivateCrtKey) generator.generateKeyPair().getPrivate(), List.of());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate an RSA key pair", e);
        }
    }

    /** {@code kid} of the active key, put in the header of every token it signs. */
    public String activeKeyId() {
        return signingKey.getKeyID();
    }

    /** {@code kid} of every key accepted for verification, active first. */
    public List<String> verificationKeyIds() {
        return verificationKeys.stream().map(JWK::getKeyID).toList();
    }

    /** The active public key, e.g. to hand to another service. */
    public RSAPublicKey activePublicKey() {
        try {
            return signingKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Active key has no public part", e);
        }
    }

    JWKSource<SecurityContext> signingSource() {
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    JWKSource<SecurityContext> verificationSource() {
        return new ImmutableJWKSet<>(new JWKSet(List.<JWK>copyOf(verificationKeys)));
    }

    private static RSAKey signingKey(RSAPrivateCrtKey privateKey, Path source) {
        RSAPublicKey publicKey = publicKeyOf(privateKey);
        checkSize(publicKey, source);
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new InvalidJwtKeyException("Cannot use the JWT signing key " + source + ": " + e.getMessage(), e);
        }
    }

    private static RSAKey rsaKey(RSAPublicKey publicKey, Path source) {
        checkSize(publicKey, source);
        try {
            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new InvalidJwtKeyException("Cannot use the JWT public key " + source + ": " + e.getMessage(), e);
        }
    }

    private static void checkSize(RSAPublicKey key, Path source) {
        int bits = key.getModulus().bitLength();
        if (bits < MIN_KEY_BITS) {
            throw new InvalidJwtKeyException(
                    "JWT key " + source + " has " + bits + " bits; at least " + MIN_KEY_BITS + " are required.");
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(Path file) {
        String pem = read(file);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new InvalidJwtKeyException("JWT signing key " + file + " is in PKCS#1 format (BEGIN RSA PRIVATE KEY);"
                    + " convert it with: openssl pkcs8 -topk8 -nocrypt -in <file> -out <new file>");
        }
        if (pem.contains("ENCRYPTED")) {
            throw new InvalidJwtKeyException(
                    "JWT signing key " + file + " is encrypted; an unencrypted key is expected.");
        }
        byte[] der = decode(pem, "PRIVATE KEY", file);
        try {
            if (KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der))
                    instanceof RSAPrivateCrtKey key) {
                return key;
            }
            throw new InvalidJwtKeyException("JWT signing key " + file + " lacks its public parameters.");
        } catch (GeneralSecurityException e) {
            throw new InvalidJwtKeyException("JWT signing key " + file + " is not an RSA private key.", e);
        }
    }

    private static RSAPublicKey readPublicKey(Path file) {
        byte[] der = decode(read(file), "PUBLIC KEY", file);
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new InvalidJwtKeyException("JWT public key " + file + " is not an RSA public key.", e);
        }
    }

    private static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive the public key", e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.US_ASCII);
        } catch (NoSuchFileException e) {
            throw new InvalidJwtKeyException("JWT key file not found: " + file, e);
        } catch (IOException e) {
            throw new InvalidJwtKeyException("JWT key file cannot be read: " + file + " (" + e.getMessage() + ")", e);
        }
    }

    private static byte[] decode(String pem, String label, Path file) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new InvalidJwtKeyException("JWT key file " + file + " has no '" + begin + "' block.");
        }
        try {
            return Base64.getMimeDecoder().decode(pem.substring(start + begin.length(), stop));
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtKeyException("JWT key file " + file + " is not valid PEM.", e);
        }
    }
}
