package pl.kupony.domain.port.in;

import java.time.Instant;
import java.util.UUID;

public interface RedeemCouponUseCase {

    RedemptionResult redeem(RedeemCouponCommand command);

    record RedeemCouponCommand(String code, UUID userId, String ip) {
    }

    record RedemptionResult(UUID redemptionId, String couponCode, UUID userId, Instant redeemedAt, int remainingUses) {
    }
}
