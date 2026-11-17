package pl.kupony.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.kupony.integration.StubIpIntelligence.DE_IP;
import static pl.kupony.integration.StubIpIntelligence.PL_IP;
import static pl.kupony.integration.StubIpIntelligence.TOR_IP;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivalTaskRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "coupons.archival.enabled=false")
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, StubIpIntelligence.class})
class RedemptionIT {

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner cleaner;

    @Autowired
    ActiveCouponRepository coupons;

    @Autowired
    RedemptionRepository redemptions;

    @Autowired
    ArchivalTaskRepository tasks;

    ApiClient api;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        api = new ApiClient(port);
    }

    @Test
    void redeemsCouponCaseInsensitivelyAndCountsDown() {
        api.createCoupon("WIOSNA", "PL", 3);

        var first = api.redeem("wiosna", UUID.randomUUID(), PL_IP);
        var second = api.redeem("WIOSNA", UUID.randomUUID(), PL_IP);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.json()).containsEntry("couponCode", "WIOSNA").containsEntry("remainingUses", 2);
        assertThat(first.json()).containsKeys("redemptionId", "userId", "redeemedAt");
        assertThat(second.json()).containsEntry("remainingUses", 1);
        assertThat(coupons.findAll()).singleElement().satisfies(c -> assertThat(c.getUsedCount()).isEqualTo(2));
    }

    @Test
    void sameUserCannotRedeemTwiceAndCounterIsUntouched() {
        api.createCoupon("WIOSNA", "PL", 3);
        var user = UUID.randomUUID();
        api.redeem("WIOSNA", user, PL_IP);

        var again = api.redeem("WIOSNA", user, PL_IP);

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.errorCode()).isEqualTo("COUPON_ALREADY_REDEEMED");
        assertThat(coupons.findAll()).singleElement().satisfies(c -> assertThat(c.getUsedCount()).isEqualTo(1));
        assertThat(redemptions.count()).isEqualTo(1);
    }

    @Test
    void lastUseExhaustsCouponAndRegistersArchivalTask() {
        api.createCoupon("WIOSNA", "PL", 1);

        assertThat(api.redeem("WIOSNA", UUID.randomUUID(), PL_IP).status()).isEqualTo(201);
        var late = api.redeem("WIOSNA", UUID.randomUUID(), PL_IP);

        assertThat(late.status()).isEqualTo(409);
        assertThat(late.errorCode()).isEqualTo("COUPON_EXHAUSTED");
        assertThat(coupons.findAll()).singleElement().satisfies(c -> {
            assertThat(c.isExhausted()).isTrue();
            assertThat(c.getActiveCode()).isNull();
        });
        assertThat(tasks.findAll()).singleElement().satisfies(t -> assertThat(t.getStatus()).isEqualTo("PENDING"));
    }

    @Test
    void exhaustedCodeCanBeReusedForANewCoupon() {
        api.createCoupon("WIOSNA", "PL", 1);
        api.redeem("WIOSNA", UUID.randomUUID(), PL_IP);

        assertThat(api.createCoupon("WIOSNA", "PL", 5).status()).isEqualTo(201);
        var redeemed = api.redeem("WIOSNA", UUID.randomUUID(), PL_IP);

        assertThat(redeemed.status()).isEqualTo(201);
        assertThat(redeemed.json()).containsEntry("remainingUses", 4);
    }

    @Test
    void unknownAndMalformedCodesAreNotFound() {
        assertThat(api.redeem("NOPE", UUID.randomUUID(), PL_IP).errorCode()).isEqualTo("COUPON_NOT_FOUND");
        var malformed = api.redeem("x".repeat(40), UUID.randomUUID(), PL_IP);
        assertThat(malformed.status()).isEqualTo(404);
        assertThat(malformed.errorCode()).isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void countryAndVpnRulesAreEnforced() {
        api.createCoupon("WIOSNA", "PL", 5);

        var german = api.redeem("WIOSNA", UUID.randomUUID(), DE_IP);
        var tor = api.redeem("WIOSNA", UUID.randomUUID(), TOR_IP);

        assertThat(german.status()).isEqualTo(403);
        assertThat(german.errorCode()).isEqualTo("COUPON_COUNTRY_MISMATCH");
        assertThat(tor.status()).isEqualTo(403);
        assertThat(tor.errorCode()).isEqualTo("VPN_DETECTED");
        assertThat(redemptions.count()).isZero();
    }

    @Test
    void privateIpIsInvalid() {
        api.createCoupon("WIOSNA", "PL", 5);

        var response = api.redeem("WIOSNA", UUID.randomUUID(), "192.168.1.1");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_IP_ADDRESS");
    }
}
