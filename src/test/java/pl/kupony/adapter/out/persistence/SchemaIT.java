package pl.kupony.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.entity.ActiveCouponEntity;
import pl.kupony.adapter.out.persistence.entity.RedemptionEntity;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SchemaIT {

    @Autowired
    ActiveCouponRepository coupons;

    @Autowired
    RedemptionRepository redemptions;

    @Test
    void activeCodeIsUniqueAmongActive() {
        coupons.saveAndFlush(coupon("WIOSNA", "wiosna", 0, 10));

        assertThatThrownBy(() -> coupons.saveAndFlush(coupon("wiosna", "wiosna", 0, 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exhaustedCouponsMayShareCode() {
        coupons.saveAndFlush(coupon("WIOSNA", null, 10, 10));
        coupons.saveAndFlush(coupon("WIOSNA", null, 5, 5));

        assertThat(coupons.count()).isEqualTo(2);
    }

    @Test
    void maxUsesHasNoUpperBound() {
        coupons.saveAndFlush(coupon("LATO", "lato", 0, Integer.MAX_VALUE));

        assertThat(coupons.count()).isEqualTo(1);
    }

    @Test
    void maxUsesMustBePositive() {
        assertThatThrownBy(() -> coupons.saveAndFlush(coupon("LATO", "lato", 0, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usedCountCannotExceedMaxUses() {
        assertThatThrownBy(() -> coupons.saveAndFlush(coupon("LATO", "lato", 3, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userCanRedeemCouponOnlyOnce() {
        var coupon = coupons.saveAndFlush(coupon("JESIEN", "jesien", 0, 10));
        var userId = UUID.randomUUID();
        redemptions.saveAndFlush(redemption(coupon.getId(), userId));

        assertThatThrownBy(() -> redemptions.saveAndFlush(redemption(coupon.getId(), userId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incrementIfAvailableStopsAtMaxUses() {
        var id = coupons.saveAndFlush(coupon("ZIMA", "zima", 0, 2)).getId();

        assertThat(coupons.incrementIfAvailable(id)).isEqualTo(1);
        assertThat(coupons.incrementIfAvailable(id)).isEqualTo(1);
        assertThat(coupons.incrementIfAvailable(id)).isZero();

        var reloaded = coupons.findById(id).orElseThrow();
        assertThat(reloaded.getUsedCount()).isEqualTo(2);
        assertThat(reloaded.isExhausted()).isTrue();
        assertThat(reloaded.getActiveCode()).isNull();
    }

    private static ActiveCouponEntity coupon(String code, String activeCode, int used, int max) {
        var entity = new ActiveCouponEntity();
        entity.setCode(code);
        entity.setNormalizedCode(code.toLowerCase());
        entity.setActiveCode(activeCode);
        entity.setCountry("PL");
        entity.setMaxUses(max);
        entity.setUsedCount(used);
        entity.setExhausted(used >= max);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private static RedemptionEntity redemption(UUID couponId, UUID userId) {
        var entity = new RedemptionEntity();
        entity.setCouponId(couponId);
        entity.setUserId(userId);
        entity.setIp("83.12.34.56");
        entity.setCountry("PL");
        entity.setRedeemedAt(Instant.now());
        return entity;
    }
}
