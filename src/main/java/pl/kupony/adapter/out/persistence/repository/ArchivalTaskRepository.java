package pl.kupony.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.kupony.adapter.out.persistence.entity.ArchivalTaskEntity;

public interface ArchivalTaskRepository extends JpaRepository<ArchivalTaskEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ArchivalTaskEntity t
               SET t.leaseUntil = :until
             WHERE t.couponId = :id AND t.status = 'PENDING'
               AND (t.leaseUntil IS NULL OR t.leaseUntil < :now)
            """)
    int tryLease(UUID id, Instant now, Instant until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ArchivalTaskEntity t SET t.leaseUntil = :until WHERE t.couponId = :id")
    int setLease(UUID id, Instant until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ArchivalTaskEntity t
               SET t.status = CASE WHEN t.attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE t.status END,
                   t.lastError = :error,
                   t.leaseUntil = :retryAt,
                   t.attempts = t.attempts + 1
             WHERE t.couponId = :id
            """)
    int markFailure(UUID id, String error, int maxAttempts, Instant retryAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ArchivalTaskEntity t WHERE t.couponId = :id")
    int deleteTask(UUID id);

    @Query("""
            SELECT t.couponId FROM ArchivalTaskEntity t
             WHERE t.status = 'PENDING' AND (t.leaseUntil IS NULL OR t.leaseUntil < :now)
             ORDER BY t.createdAt
            """)
    List<UUID> findClaimable(Instant now, Limit limit);

    long countByStatus(String status);
}
