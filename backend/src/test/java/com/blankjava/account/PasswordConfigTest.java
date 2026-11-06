package com.blankjava.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The production hashing cost (the test profile lowers it to keep the suites fast). */
class PasswordConfigTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(PasswordConfig.class);

    @Test
    void byDefaultHashesAreArgon2idWithOwaspMinimumCost() {
        context.run(app -> {
            PasswordEncoder encoder = app.getBean(PasswordEncoder.class);
            String hash = encoder.encode("tangerine-river-boat");

            // The encoded hash carries its parameters: memory 19 MiB, 2 iterations, parallelism 1
            assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
            assertThat(encoder.matches("tangerine-river-boat", hash)).isTrue();
            assertThat(encoder.matches("tangerine-river-boaT", hash)).isFalse();
        });
    }

    @Test
    void theCostIsConfigurable() {
        context.withPropertyValues(
                        "blankjava.security.password.argon2.memory-kib=65536",
                        "blankjava.security.password.argon2.iterations=3",
                        "blankjava.security.password.argon2.parallelism=2")
                .run(app -> assertThat(app.getBean(PasswordEncoder.class).encode("tangerine-river-boat"))
                        .startsWith("{argon2}$argon2id$v=19$m=65536,t=3,p=2$"));
    }

    @Test
    void applicationYamlKeepsTheOwaspMinimum() throws Exception {
        // classpath:/application.yaml is the main one; the test override lives in /config/
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("main", new ClassPathResource("application.yaml"));
        PasswordProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bindOrCreate("blankjava.security.password", PasswordProperties.class);

        assertThat(properties.argon2()).isEqualTo(new PasswordProperties.Argon2(19456, 2, 1));
    }
}
