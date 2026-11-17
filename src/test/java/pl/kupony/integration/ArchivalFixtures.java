package pl.kupony.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.adapter.out.persistence.entity.ActiveCouponEntity;
import pl.kupony.adapter.out.persistence.entity.ArchivalTaskEntity;
import pl.kupony.adapter.out.persistence.entity.RedemptionEntity;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivalTaskRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;

@TestComponent
class ArchivalFixtures {

    private final ActiveCouponRepository coupons;
    private final RedemptionRepository redemptions;
    private final ArchivalTaskRepository tasks;

    ArchivalFixtures(ActiveCouponRepository coupons, RedemptionRepository redemptions, ArchivalTaskRepository tasks) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.tasks = tasks;
    }

    @Transactional
    ExhaustedCoupon exhaustedCoupon(String code, int uses, Instant leaseUntil) {
        var coupon = new ActiveCouponEntity();
        coupon.setCode(code);
        coupon.setNormalizedCode(code.toLowerCase());
        coupon.setActiveCode(null);
        coupon.setCountry("PL");
        coupon.setMaxUses(uses);
        coupon.setUsedCount(uses);
        coupon.setExhausted(true);
        coupon.setCreatedAt(Instant.now());
        UUID id = coupons.save(coupon).getId();

        List<UUID> users = new ArrayList<>();
        List<RedemptionEntity> rows = new ArrayList<>();
        for (int i = 0; i < uses; i++) {
            var r = new RedemptionEntity();
            r.setCouponId(id);
            UUID user = UUID.randomUUID();
            users.add(user);
            r.setUserId(user);
            r.setIp("212.77.98.9");
            r.setCountry("PL");
            r.setRedeemedAt(Instant.now());
            rows.add(r);
        }
        redemptions.saveAll(rows);

        var task = new ArchivalTaskEntity();
        task.setCouponId(id);
        task.setStatus(ArchivalTaskEntity.PENDING);
        task.setCreatedAt(Instant.now());
        task.setLeaseUntil(leaseUntil);
        tasks.save(task);
        return new ExhaustedCoupon(id, code, users);
    }

    record ExhaustedCoupon(UUID id, String code, List<UUID> users) {
    }
}
