package pl.kupony.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
            "VALIDATION_FAILED, 400",
            "INVALID_IP_ADDRESS, 400",
            "COUPON_NOT_FOUND, 404",
            "COUPON_CODE_ALREADY_ACTIVE, 409",
            "COUPON_EXHAUSTED, 409",
            "COUPON_ALREADY_REDEEMED, 409",
            "COUPON_COUNTRY_MISMATCH, 403",
            "VPN_DETECTED, 403",
            "GEOLOCATION_UNAVAILABLE, 503",
            "COUPON_BUSY, 503"
    })
    void mapsToHttpStatusFromSpec(ErrorCode code, int status) {
        assertThat(code.httpStatus()).isEqualTo(status);
    }

    @Test
    void exceptionCarriesCodeMessageAndOptionalRetryAfter() {
        var plain = new CouponException(ErrorCode.COUPON_EXHAUSTED, "gone");
        var retry = new CouponException(ErrorCode.COUPON_BUSY, "busy", Duration.ofSeconds(1));

        assertThat(plain.code()).isEqualTo(ErrorCode.COUPON_EXHAUSTED);
        assertThat(plain.getMessage()).isEqualTo("gone");
        assertThat(plain.retryAfter()).isEmpty();
        assertThat(retry.retryAfter()).contains(Duration.ofSeconds(1));
    }
}
