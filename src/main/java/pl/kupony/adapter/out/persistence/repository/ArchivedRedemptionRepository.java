package pl.kupony.adapter.out.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.kupony.adapter.out.persistence.entity.ArchivedRedemptionEntity;

public interface ArchivedRedemptionRepository extends JpaRepository<ArchivedRedemptionEntity, UUID> {
}
