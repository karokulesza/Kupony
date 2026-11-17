package pl.kupony.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import pl.kupony.domain.port.in.RedeemCouponUseCase.RedemptionResult;

public record RedemptionResponse(UUID redemptionId, String couponCode, UUID userId, Instant redeemedAt,
                                 int remainingUses) {

    public static RedemptionResponse from(RedemptionResult result) {
        return new RedemptionResponse(result.redemptionId(), result.couponCode(), result.userId(),
                result.redeemedAt(), result.remainingUses());
    }
}
