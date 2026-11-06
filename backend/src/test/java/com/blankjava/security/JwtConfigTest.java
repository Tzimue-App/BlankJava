package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Where the signing key comes from, and the startup error when it is missing or unusable. */
class JwtConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "blankjava.security.allowed-origins=https://localhost",
                    "blankjava.security.jwt.issuer=blankjava-backend",
                    "blankjava.security.jwt.audience=blankjava-web",
                    "blankjava.security.jwt.access-token-lifetime=10m",
                    "blankjava.security.jwt.clock-skew=30s");

    @TempDir
    Path dir;

    @Test
    void loadsTheKeysFromTheConfiguredFiles() throws Exception {
        Path active = Files.writeString(dir.resolve("active.pem"), TestKeys.privatePem(TestKeys.ACTIVE));
        Path previous = Files.writeString(dir.resolve("previous.pem"), TestKeys.publicPem(TestKeys.PREVIOUS));

        context.withPropertyValues(
                        "blankjava.security.jwt.private-key-path=" + active,
                        "blankjava.security.jwt.previous-public-key-paths=" + previous)
                .run(started -> {
                    assertThat(started).hasNotFailed();
                    assertThat(started.getBean(JwtKeySet.class).verificationKeyIds())
                            .hasSize(2);
                    assertThat(started.getBean(SecurityProperties.class).allowedOrigins())
                            .containsExactly("https://localhost");
                });
    }

    @Test
    void anEphemeralKeyIsGeneratedWhenAskedFor() {
        context.withPropertyValues("blankjava.security.jwt.ephemeral-key=true").run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(JwtKeySet.class).activeKeyId()).isNotBlank();
        });
    }

    @Test
    void startupFailsWithAClearMessageWhenNoKeyIsConfigured() {
        context.withPropertyValues("blankjava.security.jwt.private-key-path=").run(started -> {
            assertThat(started).hasFailed();
            assertThat(started.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(InvalidJwtKeyException.class)
                    .hasMessageContaining("JWT_PRIVATE_KEY_PATH");
        });
    }

    @Test
    void startupFailsNamingTheMissingFile() {
        Path missing = dir.resolve("missing.pem");

        context.withPropertyValues("blankjava.security.jwt.private-key-path=" + missing)
                .run(started -> {
                    assertThat(started).hasFailed();
                    FailureAnalysis analysis = new InvalidJwtKeyFailureAnalyzer().analyze(started.getStartupFailure());
                    assertThat(analysis).isNotNull();
                    assertThat(analysis.getDescription()).contains(missing.toString());
                    assertThat(analysis.getAction()).contains("JWT_PRIVATE_KEY_PATH", "infra/README.md");
                });
    }
}
