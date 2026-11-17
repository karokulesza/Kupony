package pl.kupony.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.kupony.adapter.out.persistence.entity.ActiveCouponEntity;
import pl.kupony.adapter.out.persistence.entity.ArchivalTaskEntity;
import pl.kupony.adapter.out.persistence.entity.ArchivedRedemptionEntity;
import pl.kupony.adapter.out.persistence.entity.RedemptionEntity;
import pl.kupony.adapter.out.persistence.entity.UsedCouponEntity;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivalTaskRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivedRedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.UsedCouponRepository;
import pl.kupony.domain.port.out.ArchivalTaskPort;

@Component
@RequiredArgsConstructor
class ArchivalPersistenceAdapter implements ArchivalTaskPort {

    private final ArchivalTaskRepository tasks;
    private final ActiveCouponRepository activeCoupons;
    private final UsedCouponRepository usedCoupons;
    private final RedemptionRepository redemptions;
    private final ArchivedRedemptionRepository archivedRedemptions;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID couponId) {
        var task = new ArchivalTaskEntity();
        task.setCouponId(couponId);
        task.setStatus(ArchivalTaskEntity.PENDING);
        task.setCreatedAt(clock.instant());
        tasks.save(task);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findClaimable(int limit) {
        return tasks.findClaimable(clock.instant(), Limit.of(limit));
    }

    @Override
    @Transactional
    public boolean tryLease(UUID couponId, Instant leaseUntil) {
        return tasks.tryLease(couponId, clock.instant(), leaseUntil) == 1;
    }

    @Override
    @Transactional
    public int moveRedemptionBatch(UUID couponId, int size, Instant leaseUntil) {
        List<RedemptionEntity> batch = redemptions.findBatchForArchival(couponId, Limit.of(size));
        if (!batch.isEmpty()) {
            archivedRedemptions.saveAll(batch.stream().map(ArchivalPersistenceAdapter::toArchived).toList());
            archivedRedemptions.flush();
            redemptions.deleteAllByIdInBatch(batch.stream().map(RedemptionEntity::getId).toList());
        }
        tasks.setLease(couponId, leaseUntil);
        return batch.size();
    }

    @Override
    @Transactional
    public void finalizeCoupon(UUID couponId) {
        activeCoupons.findByIdAndExhaustedTrue(couponId).ifPresent(coupon -> {
            usedCoupons.save(toUsed(coupon));
            activeCoupons.delete(coupon);
            activeCoupons.flush();
        });
        tasks.deleteTask(couponId);
    }

    @Override
    @Transactional
    public void releaseLease(UUID couponId) {
        tasks.setLease(couponId, null);
    }

    @Override
    @Transactional
    public void markFailure(UUID couponId, String error, int maxAttempts, Instant retryAt) {
        tasks.markFailure(couponId, error, maxAttempts, retryAt);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return tasks.countByStatus(ArchivalTaskEntity.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFailed() {
        return tasks.countByStatus(ArchivalTaskEntity.FAILED);
    }

    private static ArchivedRedemptionEntity toArchived(RedemptionEntity r) {
        var archived = new ArchivedRedemptionEntity();
        archived.setId(r.getId());
        archived.setCouponId(r.getCouponId());
        archived.setUserId(r.getUserId());
        archived.setIp(r.getIp());
        archived.setCountry(r.getCountry());
        archived.setRedeemedAt(r.getRedeemedAt());
        return archived;
    }

    private UsedCouponEntity toUsed(ActiveCouponEntity c) {
        var used = new UsedCouponEntity();
        used.setId(c.getId());
        used.setCode(c.getCode());
        used.setNormalizedCode(c.getNormalizedCode());
        used.setCountry(c.getCountry());
        used.setMaxUses(c.getMaxUses());
        used.setUsedCount(c.getUsedCount());
        used.setCreatedAt(c.getCreatedAt());
        used.setArchivedAt(clock.instant());
        return used;
    }
}
