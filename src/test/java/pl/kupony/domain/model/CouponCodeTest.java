package pl.kupony.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

class CouponCodeTest {

    @Test
    void codesDifferingOnlyInCaseHaveSameNormalizedForm() {
        assertThat(new CouponCode("WIOSNA").normalized()).isEqualTo(new CouponCode("wiosna").normalized());
    }

    @Test
    void keepsOriginalSpelling() {
        assertThat(new CouponCode("WiOsNa").value()).isEqualTo("WiOsNa");
    }

    @Test
    void normalizationIgnoresDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(new CouponCode("WIOSNA").normalized()).isEqualTo("wiosna");
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab", "abcdefghijabcdefghijabcdefghijabc", "WI OSNA", "wiosna!", "zniżka"})
    void rejectsInvalidFormat(String raw) {
        assertThatThrownBy(() -> new CouponCode(raw))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "SUMMER-2026", "abcdefghijabcdefghijabcdefghijab"})
    void acceptsValidFormat(String raw) {
        assertThat(new CouponCode(raw).value()).isEqualTo(raw);
    }

    @Test
    void isValidReportsFormatWithoutThrowing() {
        assertThat(CouponCode.isValid("WIOSNA")).isTrue();
        assertThat(CouponCode.isValid("WI OSNA")).isFalse();
        assertThat(CouponCode.isValid(null)).isFalse();
    }
}
