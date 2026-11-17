package pl.kupony.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCouponRequest(
        @NotBlank String code,
        @NotBlank String country,
        @NotNull @Min(1) Integer maxUses) {
}
