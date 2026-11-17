package pl.kupony.adapter.out.persistence.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import pl.kupony.adapter.out.persistence.entity.ActiveCouponEntity;

public interface ActiveCouponRepository extends JpaRepository<ActiveCouponEntity, UUID> {

    String LOCK_WAIT_TIMEOUT_MILLIS = "2000";

    List<ActiveCouponEntity> findByNormalizedCodeOrderByExhaustedAsc(String normalizedCode, Limit limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = LOCK_WAIT_TIMEOUT_MILLIS))
    @Query("""
            UPDATE ActiveCouponEntity c
               SET c.exhausted  = CASE WHEN c.usedCount + 1 >= c.maxUses THEN true ELSE false END,
                   c.activeCode = CASE WHEN c.usedCount + 1 >= c.maxUses THEN NULL ELSE c.activeCode END,
                   c.usedCount  = c.usedCount + 1
             WHERE c.id = :id AND c.exhausted = false
            """)
    int incrementIfAvailable(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActiveCouponEntity> findByIdAndExhaustedTrue(UUID id);
}
