package pl.kupony.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import pl.kupony.domain.model.Coupon;

public record CouponResponse(UUID id, String code, String country, int maxUses, int usedCount, Instant createdAt) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.id(), coupon.code().value(), coupon.country(), coupon.maxUses(),
                coupon.usedCount(), coupon.createdAt());
    }
}
