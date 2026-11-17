package pl.kupony.domain.port.out;

import java.util.Optional;
import java.util.UUID;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;

public interface CouponRepositoryPort {

    Coupon save(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);

    boolean existsInArchive(CouponCode code);

    Optional<Coupon> incrementIfAvailable(UUID couponId);
}
