package pl.kupony.domain.port.in;

import pl.kupony.domain.model.Coupon;

public interface CreateCouponUseCase {

    Coupon create(CreateCouponCommand command);

    record CreateCouponCommand(String code, String country, int maxUses) {
    }
}
