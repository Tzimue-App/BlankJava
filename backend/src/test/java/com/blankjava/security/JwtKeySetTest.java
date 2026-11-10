package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeySetTest {

    @TempDir
    Path dir;

    @Test
    void loadsAPkcs8PrivateKeyAndDerivesItsPublicKey() throws Exception {
        Path file = write("active.pem", TestKeys.privatePem(TestKeys.ACTIVE));

        JwtKeySet keys = JwtKeySet.load(file, List.of());

        assertThat(keys.activePublicKey()).isEqualTo(TestKeys.publicKey(TestKeys.ACTIVE));
        assertThat(keys.verificationKeyIds()).containsExactly(keys.activeKeyId());
    }

    @Test
    void theKeyIdIsStableForTheSameKey() throws Exception {
        Path file = write("active.pem", TestKeys.privatePem(TestKeys.ACTIVE));

        assertThat(JwtKeySet.load(file, List.of()).activeKeyId())
                .isEqualTo(JwtKeySet.of(TestKeys.privateKey(TestKeys.ACTIVE), List.of())
                        .activeKeyId())
                .isNotBlank();
    }

    @Test
    void previousPublicKeysAreAcceptedForVerificationAfterTheActiveOne() throws Exception {
        Path active = write("active.pem", TestKeys.privatePem(TestKeys.ACTIVE));
        Path previous = write("previous.pem", TestKeys.publicPem(TestKeys.PREVIOUS));

        JwtKeySet keys = JwtKeySet.load(active, List.of(previous));

        String previousId =
                JwtKeySet.of(TestKeys.privateKey(TestKeys.PREVIOUS), List.of()).activeKeyId();
        assertThat(keys.verificationKeyIds()).containsExactly(keys.activeKeyId(), previousId);
    }

    @Test
    void aMissingFileIsReportedWithItsPath() {
        Path missing = dir.resolve("nope.pem");

        assertThatThrownBy(() -> JwtKeySet.load(missing, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(missing.toString());
    }

    @Test
    void aKeyShorterThan3072BitsIsRefused() throws Exception {
        Path file = write("small.pem", TestKeys.privatePem(TestKeys.TOO_SMALL));

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("2048 bits")
                .hasMessageContaining("3072");
    }

    @Test
    void aPreviousPublicKeyShorterThan3072BitsIsRefused() throws Exception {
        Path active = write("active.pem", TestKeys.privatePem(TestKeys.ACTIVE));
        Path small = write("small.pub.pem", TestKeys.publicPem(TestKeys.TOO_SMALL));

        assertThatThrownBy(() -> JwtKeySet.load(active, List.of(small)))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining(small.toString());
    }

    @Test
    void aPkcs1KeyIsRefusedWithTheConversionCommand() throws Exception {
        Path file = write("pkcs1.pem", "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("PKCS#1")
                .hasMessageContaining("openssl pkcs8 -topk8");
    }

    @Test
    void anEncryptedKeyIsRefused() throws Exception {
        Path file = write(
                "encrypted.pem", "-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----\n");

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("encrypted");
    }

    @Test
    void aFileWithoutAPemBlockIsRefused() throws Exception {
        Path file = write("garbage.pem", "not a key");

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("BEGIN PRIVATE KEY");
    }

    @Test
    void aPemBlockThatIsNotBase64IsRefused() throws Exception {
        Path file = write("bad.pem", "-----BEGIN PRIVATE KEY-----\n@@@@\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining(file.toString());
    }

    @Test
    void aPublicKeyGivenAsTheSigningKeyIsRefused() throws Exception {
        Path file = write("public.pem", TestKeys.publicPem(TestKeys.ACTIVE));

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("BEGIN PRIVATE KEY");
    }

    @Test
    void anInvalidPreviousPublicKeyIsRefused() throws Exception {
        Path active = write("active.pem", TestKeys.privatePem(TestKeys.ACTIVE));
        Path broken = write("broken.pub.pem", "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n");

        assertThatThrownBy(() -> JwtKeySet.load(active, List.of(broken)))
                .isInstanceOf(InvalidJwtKeyException.class)
                .hasMessageContaining("not an RSA public key");
    }

    @Test
    void errorMessagesNeverContainTheKeyMaterial() throws Exception {
        String pem = TestKeys.privatePem(TestKeys.TOO_SMALL);
        Path file = write("small.pem", pem);
        String someKeyBytes = pem.lines().skip(2).findFirst().orElseThrow();

        assertThatThrownBy(() -> JwtKeySet.load(file, List.of()))
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(someKeyBytes));
    }

    @Test
    void generatesA3072BitKey() {
        JwtKeySet keys = JwtKeySet.generate();

        assertThat(keys.activePublicKey().getModulus().bitLength()).isEqualTo(JwtKeySet.MIN_KEY_BITS);
    }

    private Path write(String name, String content) throws Exception {
        return Files.writeString(dir.resolve(name), content);
    }
}
