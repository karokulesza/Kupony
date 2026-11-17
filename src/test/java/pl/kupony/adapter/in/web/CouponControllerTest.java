package pl.kupony.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.port.in.CreateCouponUseCase;
import pl.kupony.domain.port.in.RedeemCouponUseCase;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:15:30Z");
    private static final UUID ID = UUID.fromString("0b6f9c1e-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreateCouponUseCase createCoupon;

    @MockitoBean
    RedeemCouponUseCase redeemCoupon;

    @Test
    void createReturns201WithBodyAndNoLocation() throws Exception {
        given(createCoupon.create(any())).willReturn(
                Coupon.restore(ID, new CouponCode("WIOSNA"), "PL", 100, 0, false, NOW));

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WIOSNA","country":"PL","maxUses":100}"""))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.code").value("WIOSNA"))
                .andExpect(jsonPath("$.country").value("PL"))
                .andExpect(jsonPath("$.maxUses").value(100))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.createdAt").value("2026-11-17T10:15:30Z"));
    }

    @Test
    void createAcceptsAnyPositiveMaxUses() throws Exception {
        given(createCoupon.create(any())).willReturn(
                Coupon.restore(ID, new CouponCode("WIOSNA"), "PL", Integer.MAX_VALUE, 0, false, NOW));

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":2147483647}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxUses").value(Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{not json",
            "{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":\"abc\"}",
            "{\"country\":\"PL\",\"maxUses\":10}",
            "{\"code\":\"WIOSNA\",\"maxUses\":10}",
            "{\"code\":\"WIOSNA\",\"country\":\"PL\"}",
            "{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":0}"
    })
    void malformedCreateRequestIsValidationProblem(String body) throws Exception {
        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void fieldErrorsAreListed() throws Exception {
        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":0}"))
                .andExpect(jsonPath("$.errors[0].field").value("maxUses"));
    }

    @Test
    void businessRejectionBecomesProblemDetail() throws Exception {
        given(createCoupon.create(any()))
                .willThrow(new CouponException(ErrorCode.COUPON_CODE_ALREADY_ACTIVE, "Active coupon 'WIOSNA' exists."));

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":10}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("COUPON_CODE_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.title").value("Coupon code already active"))
                .andExpect(jsonPath("$.detail").value("Active coupon 'WIOSNA' exists."))
                .andExpect(jsonPath("$.instance").value("/api/v1/coupons"));
    }

    @Test
    void redeemReturns201WithBody() throws Exception {
        var user = UUID.fromString("3f2a9c1e-0000-4000-8000-000000000001");
        var redemption = UUID.fromString("9d1c0000-0000-4000-8000-000000000002");
        given(redeemCoupon.redeem(new RedeemCouponUseCase.RedeemCouponCommand("WIOSNA", user, "83.12.34.56")))
                .willReturn(new RedeemCouponUseCase.RedemptionResult(redemption, "WIOSNA", user, NOW, 41));

        mvc.perform(post("/api/v1/coupons/WIOSNA/redemptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user + "\",\"ip\":\"83.12.34.56\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.redemptionId").value(redemption.toString()))
                .andExpect(jsonPath("$.couponCode").value("WIOSNA"))
                .andExpect(jsonPath("$.userId").value(user.toString()))
                .andExpect(jsonPath("$.redeemedAt").value("2026-11-17T10:15:30Z"))
                .andExpect(jsonPath("$.remainingUses").value(41));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"userId\":\"x\",\"ip\":\"83.12.34.56\"}",
            "{\"ip\":\"83.12.34.56\"}",
            "{\"userId\":\"3f2a9c1e-0000-4000-8000-000000000001\"}",
            "nope"
    })
    void malformedRedeemRequestIsValidationProblem(String body) throws Exception {
        mvc.perform(post("/api/v1/coupons/WIOSNA/redemptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void retryAfterBecomesHeader() throws Exception {
        given(createCoupon.create(any()))
                .willThrow(new CouponException(ErrorCode.COUPON_BUSY, "busy", Duration.ofSeconds(3)));

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":10}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "3"));
    }

    @ParameterizedTest
    @CsvSource({"400, 1", "1000, 1", "1500, 2", "0, 1"})
    void retryAfterIsRoundedUpToWholeSeconds(long millis, String header) throws Exception {
        given(createCoupon.create(any()))
                .willThrow(new CouponException(ErrorCode.COUPON_BUSY, "busy", Duration.ofMillis(millis)));

        mvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WIOSNA\",\"country\":\"PL\",\"maxUses\":10}"))
                .andExpect(header().string("Retry-After", header));
    }
}
