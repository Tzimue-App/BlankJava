package com.blankjava.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlocklistPasswordCheckerTest {

    @Test
    void theBundledListIsLoaded() {
        assertThat(BlocklistPasswordChecker.fromClasspath().size()).isEqualTo(5328);
    }

    @Test
    void everyBundledEntryCouldPassTheLengthRuleSoNoneIsDeadWeight() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(BlocklistPasswordChecker.RESOURCE)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8).lines())
                    .allSatisfy(line -> {
                        assertThat(Normalizer.isNormalized(line, Normalizer.Form.NFKC))
                                .isTrue();
                        assertThat(line.codePointCount(0, line.length()))
                                .isBetween(PasswordPolicy.MIN_CODE_POINTS, PasswordPolicy.MAX_CODE_POINTS);
                    });
        }
    }

    @Test
    void theLookupIsCaseInsensitiveAndNormalised() {
        BlocklistPasswordChecker checker = new BlocklistPasswordChecker(Set.of("CorrectHorseBattery"));

        assertThat(checker.check("correcthorsebattery").isCompromised()).isTrue();
        // Full-width "C" (U+FF23) becomes "C" under NFKC
        assertThat(checker.check("ＣorrectHorseBattery").isCompromised()).isTrue();
        assertThat(checker.check("correct-horse-battery").isCompromised()).isFalse();
    }

    @Test
    void noPasswordIsNotCompromised() {
        assertThat(BlocklistPasswordChecker.fromClasspath().check(null).isCompromised())
                .isFalse();
    }
}
