package pl.kupony.adapter.out.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import pl.kupony.MutableClock;
import org.junit.jupiter.api.Test;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;

class IpGeoCacheTest {

    private static final ClientIp IP_A = ClientIp.of("83.12.34.56");
    private static final ClientIp IP_B = ClientIp.of("212.77.98.9");
    private static final ClientIp IP_C = ClientIp.of("8.8.8.8");
    private static final IpInfo PL = new IpInfo("PL", false);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));

    @Test
    void returnsStoredEntry() {
        var cache = new IpGeoCache(10, Duration.ofHours(1), clock);
        cache.put(IP_A, PL);

        assertThat(cache.get(IP_A)).contains(PL);
        assertThat(cache.get(IP_B)).isEmpty();
    }

    @Test
    void entryExpiresAfterTtl() {
        var cache = new IpGeoCache(10, Duration.ofHours(1), clock);
        cache.put(IP_A, PL);

        clock.advance(Duration.ofMinutes(59));
        assertThat(cache.get(IP_A)).contains(PL);

        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get(IP_A)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void evictsLeastRecentlyUsedWhenFull() {
        var cache = new IpGeoCache(2, Duration.ofHours(1), clock);
        cache.put(IP_A, PL);
        cache.put(IP_B, PL);
        cache.get(IP_A);

        cache.put(IP_C, PL);

        assertThat(cache.get(IP_A)).isPresent();
        assertThat(cache.get(IP_B)).isEmpty();
        assertThat(cache.get(IP_C)).isPresent();
        assertThat(cache.size()).isEqualTo(2);
    }
}
