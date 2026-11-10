package com.blankjava.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import org.junit.jupiter.api.Test;

/** Password rules of #29: lengths in code points after NFKC, and the bundled blocklist. */
class PasswordPolicyTest {

    private static final PasswordPolicy POLICY = new PasswordPolicy(BlocklistPasswordChecker.fromClasspath());

    /** A 4-byte UTF-8 character (U+1F600), two UTF-16 units. */
    private static final String EMOJI = "😀";

    @Test
    void fifteenToSixtyFourCodePointsAreAccepted() {
        assertThat(check("tangerine-river")).isNull();
        assertThat(check("tangerine-river-".repeat(4))).isNull();
    }

    @Test
    void fourteenCodePointsAreTooShort() {
        assertThat(check("tangerine-rive")).extracting(FieldError::getCode).isEqualTo(CodeEnum.TOO_SHORT);
    }

    @Test
    void sixtyFiveCodePointsAreTooLong() {
        assertThat(check("tangerine-river-".repeat(4) + "x"))
                .extracting(FieldError::getCode)
                .isEqualTo(CodeEnum.TOO_LONG);
    }

    @Test
    void sixtyFourFourByteCharactersAreAccepted() {
        String password = EMOJI.repeat(64);

        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(256);
        assertThat(password).hasSize(128); // UTF-16 units: what the contract's maxLength counts
        assertThat(check(password)).isNull();
    }

    @Test
    void lengthIsCountedAfterNfkcNormalisation() {
        // U+FB00 (ligature "ff") is one code point, two after NFKC: 14 code points typed, 15 checked
        String typed = "ﬀ" + "abcdefghijklm";

        assertThat(typed.codePointCount(0, typed.length())).isEqualTo(14);
        assertThat(PasswordPolicy.normalize(typed)).isEqualTo("ffabcdefghijklm");
        assertThat(check(typed)).isNull();
    }

    @Test
    void fullWidthCharactersAreNormalisedToTheirAsciiForm() {
        // Same password typed on a keyboard producing full-width forms (U+FF21…)
        assertThat(PasswordPolicy.normalize("Ｔａｎｇｅｒｉｎｅ")).isEqualTo("Tangerine");
    }

    @Test
    void aPasswordOfTheBlocklistIsCompromisedWhateverItsCase() {
        assertThat(check("demon1q2w3e4r5t")).extracting(FieldError::getCode).isEqualTo(CodeEnum.COMPROMISED_PASSWORD);
        assertThat(check("DEMON1Q2W3E4R5T")).extracting(FieldError::getCode).isEqualTo(CodeEnum.COMPROMISED_PASSWORD);
    }

    @Test
    void errorMessagesNeverContainThePassword() {
        assertThat(check("tangerine-rive").getMessage()).doesNotContain("tangerine");
        assertThat(check("demon1q2w3e4r5t").getMessage()).doesNotContain("demon");
        assertThat(check("demon1q2w3e4r5t").getField()).isEqualTo("password");
    }

    private static FieldError check(String password) {
        return POLICY.check(PasswordPolicy.normalize(password)).orElse(null);
    }
}
