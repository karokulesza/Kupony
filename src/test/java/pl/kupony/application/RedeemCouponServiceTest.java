package pl.kupony.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import pl.kupony.application.archival.CouponExhaustedEvent;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.model.IpInfo;
import pl.kupony.domain.model.Redemption;
import pl.kupony.domain.port.in.RedeemCouponUseCase.RedeemCouponCommand;
import pl.kupony.domain.port.in.RedeemCouponUseCase.RedemptionResult;
import pl.kupony.domain.port.out.IpIntelligencePort;

class RedeemCouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:16:02Z");
    private static final String PL_IP = "212.77.98.9";
    private static final UUID USER = UUID.fromString("3f2a9c1e-0000-4000-8000-000000000001");

    private final InMemoryCouponRepository coupons = new InMemoryCouponRepository();
    private final InMemoryRedemptionRepository redemptions = new InMemoryRedemptionRepository();
    private final RecordingArchivalTasks archivalTasks = new RecordingArchivalTasks();
    private final List<Object> events = new ArrayList<>();
    private final RedemptionWriter writer = new RedemptionWriter(coupons, redemptions, archivalTasks, events::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private IpInfo geoAnswer = new IpInfo("PL", false);
    private int geoCalls;
    private final IpIntelligencePort geo = ip -> {
        geoCalls++;
        return geoAnswer;
    };

    private RedeemCouponService service(boolean revealExhaustedReason) {
        return new RedeemCouponService(coupons, geo, writer, revealExhaustedReason);
    }

    private Coupon coupon(String code, int maxUses) {
        return coupons.save(Coupon.create(new CouponCode(code), "PL", maxUses, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static void assertRejected(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CouponException.class,
                e -> assertThat(e.code()).isEqualTo(expected));
    }

    @Test
    void redeemsCouponAndReportsRemainingUses() {
        coupon("WIOSNA", 3);

        RedemptionResult result = service(true).redeem(new RedeemCouponCommand("wiosna", USER, PL_IP));

        assertThat(result.redemptionId()).isNotNull();
        assertThat(result.couponCode()).isEqualTo("WIOSNA");
        assertThat(result.userId()).isEqualTo(USER);
        assertThat(result.redeemedAt()).isEqualTo(NOW);
        assertThat(result.remainingUses()).isEqualTo(2);
        assertThat(redemptions.saved()).singleElement()
                .satisfies(r -> assertThat(r.country()).isEqualTo("PL"));
        assertThat(archivalTasks.enqueued).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void lastUseEnqueuesArchivalAndPublishesEvent() {
        Coupon coupon = coupon("WIOSNA", 1);

        RedemptionResult result = service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP));

        assertThat(result.remainingUses()).isZero();
        assertThat(archivalTasks.enqueued).containsExactly(coupon.id());
        assertThat(events).containsExactly(new CouponExhaustedEvent(coupon.id()));
    }

    @Test
    void unknownCodeIsNotFound() {
        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("NOPE", USER, PL_IP)),
                ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void malformedCodeIsNotFound() {
        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("WI OSNA", USER, PL_IP)),
                ErrorCode.COUPON_NOT_FOUND);
        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("x".repeat(40), USER, PL_IP)),
                ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void invalidIpIsRejectedBeforeLookup() {
        coupon("WIOSNA", 3);

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, "10.0.0.1")),
                ErrorCode.INVALID_IP_ADDRESS);
        assertThat(geoCalls).isZero();
    }

    @Test
    void archivedCouponIsExhaustedOrNotFoundDependingOnFlag() {
        coupons.archive("LATO");

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("LATO", USER, PL_IP)),
                ErrorCode.COUPON_EXHAUSTED);
        assertRejected(() -> service(false).redeem(new RedeemCouponCommand("LATO", USER, PL_IP)),
                ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void exhaustedCouponAwaitingArchivalIsExhaustedOrNotFoundDependingOnFlag() {
        coupon("ZIMA", 1);
        service(true).redeem(new RedeemCouponCommand("ZIMA", USER, PL_IP));

        var other = UUID.randomUUID();
        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("ZIMA", other, PL_IP)),
                ErrorCode.COUPON_EXHAUSTED);
        assertRejected(() -> service(false).redeem(new RedeemCouponCommand("ZIMA", other, PL_IP)),
                ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void exhaustedCouponDoesNotCallGeolocation() {
        coupons.archive("LATO");

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("LATO", USER, PL_IP)),
                ErrorCode.COUPON_EXHAUSTED);
        assertThat(geoCalls).isZero();
    }

    @Test
    void geolocationFailureRejectsWithoutTouchingTheCounter() {
        Coupon coupon = coupon("WIOSNA", 3);
        IpIntelligencePort failing = ip -> {
            throw new CouponException(ErrorCode.GEOLOCATION_UNAVAILABLE, "down");
        };
        var service = new RedeemCouponService(coupons, failing, writer, true);

        assertRejected(() -> service.redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP)),
                ErrorCode.GEOLOCATION_UNAVAILABLE);
        assertThat(redemptions.saved()).isEmpty();
        assertThat(coupons.findByCode(coupon.code()).orElseThrow().usedCount()).isZero();
    }

    @Test
    void anonymizedIpIsVpnDetected() {
        coupon("WIOSNA", 3);
        geoAnswer = new IpInfo("PL", true);

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP)),
                ErrorCode.VPN_DETECTED);
        assertThat(redemptions.saved()).isEmpty();
    }

    @Test
    void ipFromOtherCountryIsCountryMismatch() {
        coupon("WIOSNA", 3);
        geoAnswer = new IpInfo("DE", false);

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP)),
                ErrorCode.COUPON_COUNTRY_MISMATCH);
        assertThat(redemptions.saved()).isEmpty();
    }

    @Test
    void secondUseBySameUserIsAlreadyRedeemed() {
        coupon("WIOSNA", 3);
        service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP));

        assertRejected(() -> service(true).redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP)),
                ErrorCode.COUPON_ALREADY_REDEEMED);
    }

    @Test
    void lockContentionIsBusyWithRetryAfter() {
        coupon("WIOSNA", 3);
        var busyWriter = new RedemptionWriter(coupons, redemptions, archivalTasks, events::add,
                Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override
            public java.util.Optional<RedemptionResult> consume(Coupon coupon, UUID userId,
                                                                pl.kupony.domain.model.ClientIp ip,
                                            String country) {
                throw new CannotAcquireLockException("lock wait timeout");
            }
        };
        var service = new RedeemCouponService(coupons, geo, busyWriter, true);

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", USER, PL_IP)))
                .isInstanceOfSatisfying(CouponException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.COUPON_BUSY);
                    assertThat(e.retryAfter()).isPresent();
                });
    }

    static final class InMemoryRedemptionRepository implements pl.kupony.domain.port.out.RedemptionRepositoryPort {

        private final List<Redemption> saved = new ArrayList<>();

        @Override
        public UUID save(Redemption redemption) {
            boolean duplicate = saved.stream().anyMatch(r -> r.couponId().equals(redemption.couponId())
                    && r.userId().equals(redemption.userId()));
            if (duplicate) {
                throw new CouponException(ErrorCode.COUPON_ALREADY_REDEEMED, "duplicate");
            }
            saved.add(redemption);
            return UUID.randomUUID();
        }

        List<Redemption> saved() {
            return saved;
        }
    }
}
