package pl.kupony.adapter.out.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.kupony.adapter.out.persistence.entity.UsedCouponEntity;

public interface UsedCouponRepository extends JpaRepository<UsedCouponEntity, UUID> {

    boolean existsByNormalizedCode(String normalizedCode);
}
