package pl.kupony.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.UsedCouponRepository;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.port.out.CouponRepositoryPort;

@Component
@RequiredArgsConstructor
class CouponPersistenceAdapter implements CouponRepositoryPort {

    private final ActiveCouponRepository activeCoupons;
    private final UsedCouponRepository usedCoupons;

    @Override
    public Coupon save(Coupon coupon) {
        try {
            return PersistenceMapper.toDomain(activeCoupons.saveAndFlush(PersistenceMapper.toNewEntity(coupon)));
        } catch (DataIntegrityViolationException e) {
            throw new CouponException(ErrorCode.COUPON_CODE_ALREADY_ACTIVE,
                    "An active coupon with code '" + coupon.code().value() + "' already exists.");
        }
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return activeCoupons.findByNormalizedCodeOrderByExhaustedAsc(code.normalized(), Limit.of(1)).stream()
                .findFirst()
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public boolean existsInArchive(CouponCode code) {
        return usedCoupons.existsByNormalizedCode(code.normalized());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Coupon> incrementIfAvailable(UUID couponId) {
        if (activeCoupons.incrementIfAvailable(couponId) == 0) {
            return Optional.empty();
        }
        return activeCoupons.findById(couponId).map(PersistenceMapper::toDomain);
    }
}
