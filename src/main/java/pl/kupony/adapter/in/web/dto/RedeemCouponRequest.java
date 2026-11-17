package pl.kupony.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RedeemCouponRequest(@NotNull UUID userId, @NotBlank String ip) {
}
