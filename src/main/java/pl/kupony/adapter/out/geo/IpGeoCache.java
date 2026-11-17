package pl.kupony.adapter.out.geo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;

public class IpGeoCache {

    private final Duration ttl;
    private final Clock clock;
    private final Map<ClientIp, CachedInfo> entries;

    public IpGeoCache(int maxSize, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ClientIp, CachedInfo> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized Optional<IpInfo> get(ClientIp ip) {
        CachedInfo entry = entries.get(ip);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(ip);
            return Optional.empty();
        }
        return Optional.of(entry.info());
    }

    public synchronized void put(ClientIp ip, IpInfo info) {
        entries.put(ip, new CachedInfo(info, clock.instant().plus(ttl)));
    }

    synchronized int size() {
        return entries.size();
    }

    private record CachedInfo(IpInfo info, Instant expiresAt) {
    }
}
