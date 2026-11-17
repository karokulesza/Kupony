package pl.kupony.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kupony.adapter.in.web.dto.CouponResponse;
import pl.kupony.adapter.in.web.dto.CreateCouponRequest;
import pl.kupony.adapter.in.web.dto.RedeemCouponRequest;
import pl.kupony.adapter.in.web.dto.RedemptionResponse;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.port.in.CreateCouponUseCase.CreateCouponCommand;
import pl.kupony.domain.port.in.CreateCouponUseCase;
import pl.kupony.domain.port.in.RedeemCouponUseCase.RedeemCouponCommand;
import pl.kupony.domain.port.in.RedeemCouponUseCase;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
class CouponController {

    private final CreateCouponUseCase createCoupon;
    private final RedeemCouponUseCase redeemCoupon;

    @PostMapping
    ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        Coupon coupon = createCoupon.create(
                new CreateCouponCommand(request.code(), request.country(), request.maxUses()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CouponResponse.from(coupon));
    }

    @PostMapping("/{code}/redemptions")
    ResponseEntity<RedemptionResponse> redeem(@PathVariable String code,
                                              @Valid @RequestBody RedeemCouponRequest request) {
        var result = redeemCoupon.redeem(new RedeemCouponCommand(code, request.userId(), request.ip()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RedemptionResponse.from(result));
    }
}
