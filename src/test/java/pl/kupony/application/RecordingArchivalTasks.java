package pl.kupony.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pl.kupony.domain.port.out.ArchivalTaskPort;

class RecordingArchivalTasks implements ArchivalTaskPort {

    final List<UUID> enqueued = new ArrayList<>();

    @Override
    public void enqueue(UUID couponId) {
        enqueued.add(couponId);
    }

    @Override
    public List<UUID> findClaimable(int limit) {
        return List.copyOf(enqueued);
    }

    @Override
    public boolean tryLease(UUID couponId, Instant leaseUntil) {
        return true;
    }

    @Override
    public int moveRedemptionBatch(UUID couponId, int size, Instant leaseUntil) {
        return 0;
    }

    @Override
    public void finalizeCoupon(UUID couponId) {
        enqueued.remove(couponId);
    }

    @Override
    public void releaseLease(UUID couponId) {
    }

    @Override
    public void markFailure(UUID couponId, String error, int maxAttempts, Instant retryAt) {
    }

    @Override
    public long countPending() {
        return enqueued.size();
    }

    @Override
    public long countFailed() {
        return 0;
    }
}
