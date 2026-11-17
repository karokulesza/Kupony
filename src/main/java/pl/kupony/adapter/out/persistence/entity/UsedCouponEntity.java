package pl.kupony.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "used_coupons")
@Getter
@Setter
@NoArgsConstructor
public class UsedCouponEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "normalized_code", nullable = false, length = 32)
    private String normalizedCode;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @Override
    public boolean isNew() {
        return true;
    }
}
