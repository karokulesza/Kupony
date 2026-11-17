package pl.kupony.adapter.out.persistence;

import pl.kupony.adapter.out.persistence.entity.ActiveCouponEntity;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;

final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static Coupon toDomain(ActiveCouponEntity entity) {
        return Coupon.restore(entity.getId(), new CouponCode(entity.getCode()), entity.getCountry(),
                entity.getMaxUses(), entity.getUsedCount(), entity.isExhausted(), entity.getCreatedAt());
    }

    static ActiveCouponEntity toNewEntity(Coupon coupon) {
        var entity = new ActiveCouponEntity();
        entity.setCode(coupon.code().value());
        entity.setNormalizedCode(coupon.code().normalized());
        entity.setActiveCode(coupon.code().normalized());
        entity.setCountry(coupon.country());
        entity.setMaxUses(coupon.maxUses());
        entity.setUsedCount(coupon.usedCount());
        entity.setExhausted(false);
        entity.setCreatedAt(coupon.createdAt());
        return entity;
    }
}
