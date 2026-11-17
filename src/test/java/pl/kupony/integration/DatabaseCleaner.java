package pl.kupony.integration;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivalTaskRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivedRedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.UsedCouponRepository;

@TestComponent
class DatabaseCleaner {

    private final RedemptionRepository redemptions;
    private final ArchivalTaskRepository tasks;
    private final ActiveCouponRepository coupons;
    private final ArchivedRedemptionRepository archivedRedemptions;
    private final UsedCouponRepository usedCoupons;

    DatabaseCleaner(RedemptionRepository redemptions, ArchivalTaskRepository tasks, ActiveCouponRepository coupons,
                    ArchivedRedemptionRepository archivedRedemptions, UsedCouponRepository usedCoupons) {
        this.redemptions = redemptions;
        this.tasks = tasks;
        this.coupons = coupons;
        this.archivedRedemptions = archivedRedemptions;
        this.usedCoupons = usedCoupons;
    }

    @Transactional
    void clean() {
        redemptions.deleteAllInBatch();
        tasks.deleteAllInBatch();
        coupons.deleteAllInBatch();
        archivedRedemptions.deleteAllInBatch();
        usedCoupons.deleteAllInBatch();
    }
}
