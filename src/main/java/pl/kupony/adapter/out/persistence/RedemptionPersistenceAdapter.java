package pl.kupony.adapter.out.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.adapter.out.persistence.entity.RedemptionEntity;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Redemption;
import pl.kupony.domain.port.out.RedemptionRepositoryPort;

@Component
@RequiredArgsConstructor
class RedemptionPersistenceAdapter implements RedemptionRepositoryPort {

    private final RedemptionRepository redemptions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID save(Redemption redemption) {
        var entity = new RedemptionEntity();
        entity.setCouponId(redemption.couponId());
        entity.setUserId(redemption.userId());
        entity.setIp(redemption.ip().value());
        entity.setCountry(redemption.country());
        entity.setRedeemedAt(redemption.redeemedAt());
        try {
            return redemptions.saveAndFlush(entity).getId();
        } catch (DataIntegrityViolationException e) {
            throw new CouponException(ErrorCode.COUPON_ALREADY_REDEEMED, "This user has already used the coupon.");
        }
    }
}
