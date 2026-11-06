package com.blankjava.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blankjava.security.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OriginCheckTest {

    private final OriginCheck check =
            new OriginCheck(new SecurityProperties(List.of("https://localhost", "https://app.example:8443"), null));

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "HTTPS://LocalHost", "https://localhost/", "https://app.example:8443"})
    void anAllowedOriginPasses(String origin) {
        assertThatCode(() -> check.require(origin)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "null",
                "http://localhost",
                "https://localhost:8443",
                "https://localhost.evil.example",
                "https://evil.example",
                "https://app.example"
            })
    void aMissingOrForeignOriginIsRefused(String origin) {
        assertThatThrownBy(() -> check.require(origin)).isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void noAllowedOriginRefusesEverything() {
        OriginCheck none = new OriginCheck(new SecurityProperties(List.of(), null));

        assertThatThrownBy(() -> none.require("https://localhost")).isInstanceOf(OriginNotAllowedException.class);
    }
}
