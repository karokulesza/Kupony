package pl.kupony.adapter.out.persistence.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import pl.kupony.adapter.out.persistence.entity.RedemptionEntity;

public interface RedemptionRepository extends JpaRepository<RedemptionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT r FROM RedemptionEntity r WHERE r.couponId = :couponId")
    List<RedemptionEntity> findBatchForArchival(UUID couponId, Limit limit);
}
