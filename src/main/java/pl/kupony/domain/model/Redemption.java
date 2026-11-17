package pl.kupony.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Redemption(UUID couponId, UUID userId, ClientIp ip, String country, Instant redeemedAt) {
}
