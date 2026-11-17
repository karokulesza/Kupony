package pl.kupony.application.archival;

import java.util.UUID;

public record CouponExhaustedEvent(UUID couponId) {
}
