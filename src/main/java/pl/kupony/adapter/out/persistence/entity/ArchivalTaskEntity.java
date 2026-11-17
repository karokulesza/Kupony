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
@Table(name = "archival_task")
@Getter
@Setter
@NoArgsConstructor
public class ArchivalTaskEntity implements Persistable<UUID> {

    public static final String PENDING = "PENDING";
    public static final String FAILED = "FAILED";

    @Id
    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Override
    public UUID getId() {
        return couponId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
