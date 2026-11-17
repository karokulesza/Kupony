package pl.kupony.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createStartsUnusedWithCreationTimeAndUppercaseCountry() {
        Coupon coupon = Coupon.create(new CouponCode("WIOSNA"), "pl", 100, CLOCK);

        assertThat(coupon.id()).isNull();
        assertThat(coupon.code().value()).isEqualTo("WIOSNA");
        assertThat(coupon.country()).isEqualTo("PL");
        assertThat(coupon.maxUses()).isEqualTo(100);
        assertThat(coupon.usedCount()).isZero();
        assertThat(coupon.exhausted()).isFalse();
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(coupon.remainingUses()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsMaxUsesOutOfRange(int maxUses) {
        assertThatThrownBy(() -> Coupon.create(new CouponCode("WIOSNA"), "PL", maxUses, CLOCK))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void acceptsMaxUsesBounds() {
        assertThat(Coupon.create(new CouponCode("WIOSNA"), "PL", 1, CLOCK).maxUses()).isEqualTo(1);
        assertThat(Coupon.create(new CouponCode("WIOSNA"), "PL", Integer.MAX_VALUE, CLOCK).maxUses())
                .isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "POL", "P", "", "1A"})
    void rejectsUnknownCountry(String country) {
        assertThatThrownBy(() -> Coupon.create(new CouponCode("WIOSNA"), country, 10, CLOCK))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsNullCountry() {
        assertThatThrownBy(() -> Coupon.create(new CouponCode("WIOSNA"), null, 10, CLOCK))
                .isInstanceOf(CouponException.class);
    }

    @Test
    void restoredCouponReportsRemainingUses() {
        Coupon coupon = Coupon.restore(UUID.randomUUID(), new CouponCode("LATO"), "DE", 10, 7, false, NOW);

        assertThat(coupon.remainingUses()).isEqualTo(3);
        assertThat(coupon.exhausted()).isFalse();
    }

    @Test
    void restoredExhaustedCoupon() {
        Coupon coupon = Coupon.restore(UUID.randomUUID(), new CouponCode("LATO"), "DE", 10, 10, true, NOW);

        assertThat(coupon.remainingUses()).isZero();
        assertThat(coupon.exhausted()).isTrue();
    }
}
