package pl.kupony.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ArchivalTaskPort {

    void enqueue(UUID couponId);

    List<UUID> findClaimable(int limit);

    boolean tryLease(UUID couponId, Instant leaseUntil);

    int moveRedemptionBatch(UUID couponId, int size, Instant leaseUntil);

    void finalizeCoupon(UUID couponId);

    void releaseLease(UUID couponId);

    void markFailure(UUID couponId, String error, int maxAttempts, Instant retryAt);

    long countPending();

    long countFailed();
}
