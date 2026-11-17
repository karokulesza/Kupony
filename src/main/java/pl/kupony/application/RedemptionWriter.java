package pl.kupony.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.application.archival.CouponExhaustedEvent;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.Redemption;
import pl.kupony.domain.port.in.RedeemCouponUseCase.RedemptionResult;
import pl.kupony.domain.port.out.ArchivalTaskPort;
import pl.kupony.domain.port.out.CouponRepositoryPort;
import pl.kupony.domain.port.out.RedemptionRepositoryPort;

@Component
@RequiredArgsConstructor
public class RedemptionWriter {

    private final CouponRepositoryPort coupons;
    private final RedemptionRepositoryPort redemptions;
    private final ArchivalTaskPort archivalTasks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<RedemptionResult> consume(Coupon coupon, UUID userId, ClientIp ip, String country) {
        Optional<Coupon> incremented = coupons.incrementIfAvailable(coupon.id());
        if (incremented.isEmpty()) {
            return Optional.empty();
        }
        Coupon updated = incremented.get();
        Instant now = clock.instant();
        UUID redemptionId = redemptions.save(new Redemption(coupon.id(), userId, ip, country, now));
        if (updated.exhausted()) {
            archivalTasks.enqueue(coupon.id());
            events.publishEvent(new CouponExhaustedEvent(coupon.id()));
        }
        return Optional.of(
                new RedemptionResult(redemptionId, coupon.code().value(), userId, now, updated.remainingUses()));
    }
}
