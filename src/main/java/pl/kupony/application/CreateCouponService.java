package pl.kupony.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.port.in.CreateCouponUseCase;
import pl.kupony.domain.port.out.CouponRepositoryPort;

@Service
@RequiredArgsConstructor
public class CreateCouponService implements CreateCouponUseCase {

    private final CouponRepositoryPort coupons;
    private final Clock clock;

    @Override
    public Coupon create(CreateCouponCommand command) {
        Coupon coupon = Coupon.create(new CouponCode(command.code()), command.country(), command.maxUses(), clock);
        return coupons.save(coupon);
    }
}
