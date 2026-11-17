package pl.kupony.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.port.in.CreateCouponUseCase.CreateCouponCommand;

class CreateCouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:15:30Z");

    private final InMemoryCouponRepository repository = new InMemoryCouponRepository();
    private final CreateCouponService service = new CreateCouponService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsUnusedCouponAndStoresIt() {
        Coupon coupon = service.create(new CreateCouponCommand("WIOSNA", "pl", 100));

        assertThat(coupon.id()).isNotNull();
        assertThat(coupon.code().value()).isEqualTo("WIOSNA");
        assertThat(coupon.country()).isEqualTo("PL");
        assertThat(coupon.usedCount()).isZero();
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(repository.all()).containsExactly(coupon);
    }

    @Test
    void rejectsInvalidCode() {
        assertThatThrownBy(() -> service.create(new CreateCouponCommand("a b", "PL", 10)))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void rejectsSecondActiveCouponWithSameCodeIgnoringCase() {
        service.create(new CreateCouponCommand("WIOSNA", "PL", 10));

        assertThatThrownBy(() -> service.create(new CreateCouponCommand("wiosna", "PL", 10)))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.COUPON_CODE_ALREADY_ACTIVE));
    }
}
