package pl.kupony.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.port.out.CouponRepositoryPort;

public class InMemoryCouponRepository implements CouponRepositoryPort {

    private final List<Coupon> active = new ArrayList<>();
    private final List<String> archivedCodes = new ArrayList<>();

    @Override
    public synchronized Coupon save(Coupon coupon) {
        boolean liveDuplicate = active.stream()
                .anyMatch(c -> !c.exhausted() && c.code().normalized().equals(coupon.code().normalized()));
        if (liveDuplicate) {
            throw new CouponException(ErrorCode.COUPON_CODE_ALREADY_ACTIVE, "duplicate");
        }
        Coupon saved = coupon.id() != null ? coupon : Coupon.restore(UUID.randomUUID(), coupon.code(),
                coupon.country(), coupon.maxUses(), coupon.usedCount(), coupon.exhausted(), coupon.createdAt());
        active.add(saved);
        return saved;
    }

    @Override
    public synchronized Optional<Coupon> findByCode(CouponCode code) {
        return active.stream()
                .filter(c -> c.code().normalized().equals(code.normalized()))
                .min((a, b) -> Boolean.compare(a.exhausted(), b.exhausted()));
    }

    @Override
    public synchronized boolean existsInArchive(CouponCode code) {
        return archivedCodes.contains(code.normalized());
    }

    @Override
    public synchronized Optional<Coupon> incrementIfAvailable(UUID couponId) {
        for (int i = 0; i < active.size(); i++) {
            Coupon c = active.get(i);
            if (c.id().equals(couponId)) {
                if (c.exhausted()) {
                    return Optional.empty();
                }
                int used = c.usedCount() + 1;
                Coupon updated = Coupon.restore(c.id(), c.code(), c.country(), c.maxUses(), used,
                        used >= c.maxUses(), c.createdAt());
                active.set(i, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    public synchronized void archive(String code) {
        archivedCodes.add(new CouponCode(code).normalized());
    }

    public synchronized List<Coupon> all() {
        return List.copyOf(active);
    }
}
