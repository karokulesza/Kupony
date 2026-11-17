package pl.kupony.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

public record Coupon(UUID id, CouponCode code, String country, int maxUses, int usedCount, boolean exhausted,
                     Instant createdAt) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public static Coupon create(CouponCode code, String country, int maxUses, Clock clock) {
        if (maxUses < 1) {
            throw new CouponException(ErrorCode.VALIDATION_FAILED, "maxUses must be at least 1.");
        }
        String isoCountry = country == null ? "" : country.toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(isoCountry)) {
            throw new CouponException(ErrorCode.VALIDATION_FAILED, "country must be an ISO 3166-1 alpha-2 code.");
        }
        return new Coupon(null, code, isoCountry, maxUses, 0, false, clock.instant());
    }

    public static Coupon restore(UUID id, CouponCode code, String country, int maxUses, int usedCount,
                                 boolean exhausted, Instant createdAt) {
        return new Coupon(id, code, country, maxUses, usedCount, exhausted, createdAt);
    }

    public int remainingUses() {
        return maxUses - usedCount;
    }
}
