package com.blankjava.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountRulesTest {

    @Test
    void theEmailIsTrimmedAndLowerCasedWhateverTheServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            // Turkish lower-cases "I" to a dotless "ı": Locale.ROOT must be used
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(AccountRules.normalizeEmail("  Jane.IVY@Example.COM \t")).isEqualTo("jane.ivy@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jane@example.com",
                "jane.doe+news@mail.example.co.uk",
                "o'brien@example.ie",
                "josé@exemple.fr",
                "jane@xn--bcher-kva.example",
                "a@b.io"
            })
    void validAddressesAreAccepted(String email) {
        assertThat(AccountRules.checkEmail(email)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jane",
                "jane@",
                "@example.com",
                "jane@@example.com",
                "jane@doe@example.com",
                "jane@localhost",
                "jane@example.",
                "jane@.example.com",
                "jane@-example.com",
                "jane@example-.com",
                "jane@192.168.0.1",
                ".jane@example.com",
                "jane.@example.com",
                "ja..ne@example.com",
                "ja ne@example.com",
                "jane@exa mple.com",
                "\"jane\"@example.com",
                "jane@example..com"
            })
    void invalidAddressesAreAnInvalidFormat(String email) {
        assertThat(code(AccountRules.checkEmail(email).orElse(null))).isEqualTo(CodeEnum.INVALID_FORMAT);
    }

    @Test
    void aLocalPartOfMoreThanSixtyFourCharactersIsInvalid() {
        assertThat(AccountRules.checkEmail("a".repeat(64) + "@example.com")).isEmpty();
        assertThat(code(AccountRules.checkEmail("a".repeat(65) + "@example.com").orElse(null)))
                .isEqualTo(CodeEnum.INVALID_FORMAT);
    }

    @Test
    void anEmptyEmailIsRequiredAndALongOneTooLong() {
        assertThat(code(AccountRules.checkEmail(AccountRules.normalizeEmail("   "))
                        .orElse(null)))
                .isEqualTo(CodeEnum.REQUIRED);
        String tooLong = "a".repeat(64) + "@" + ("b".repeat(63) + ".").repeat(3) + "com";
        assertThat(tooLong).hasSizeGreaterThan(AccountRules.EMAIL_MAX);
        assertThat(code(AccountRules.checkEmail(tooLong).orElse(null))).isEqualTo(CodeEnum.TOO_LONG);
    }

    @Test
    void theDisplayNameIsTrimmedThenTwoToFiftyCodePoints() {
        assertThat(code(display(" a "))).isEqualTo(CodeEnum.TOO_SHORT);
        assertThat(code(display(" 　 "))).isEqualTo(CodeEnum.REQUIRED); // ideographic space
        assertThat(display(" Jo ")).isNull();
        assertThat(display("x".repeat(50))).isNull();
        assertThat(code(display("x".repeat(51)))).isEqualTo(CodeEnum.TOO_LONG);
        // 50 emoji: 100 UTF-16 units, 50 code points
        assertThat(display("😀".repeat(50))).isNull();
        assertThat(AccountRules.normalizeDisplayName("  Jane Doe  ")).isEqualTo("Jane Doe");
    }

    @Test
    void aDisplayNameWithAControlCharacterIsAnInvalidFormat() {
        assertThat(code(display("Jane\u0000Doe"))).isEqualTo(CodeEnum.INVALID_FORMAT);
        assertThat(code(display("Jane\nDoe"))).isEqualTo(CodeEnum.INVALID_FORMAT);
    }

    @Test
    void errorsNameTheContractField() {
        assertThat(AccountRules.checkEmail("jane").orElseThrow().getField()).isEqualTo("email");
        assertThat(display("a").getField()).isEqualTo("displayName");
    }

    private static FieldError display(String raw) {
        return AccountRules.checkDisplayName(AccountRules.normalizeDisplayName(raw))
                .orElse(null);
    }

    private static CodeEnum code(FieldError error) {
        return error == null ? null : error.getCode();
    }
}
