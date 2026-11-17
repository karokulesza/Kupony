package pl.kupony.adapter.out.geo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;
import pl.kupony.domain.port.out.IpIntelligencePort;

public class IpApiClient implements IpIntelligencePort {

    private static final String PATH = "/json/{ip}?fields=status,message,countryCode,proxy,hosting";
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(5);
    private static final Duration DEFAULT_BLOCK = Duration.ofSeconds(60);
    private static final Set<String> UNRESOLVABLE = Set.of("private range", "reserved range", "invalid query");

    private final RestClient http;
    private final boolean rejectHosting;
    private final IpGeoCache cache;
    private final Clock clock;
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(Instant.MIN);

    public IpApiClient(RestClient.Builder builder, GeoProperties properties, IpGeoCache cache, Clock clock) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.rejectHosting = properties.rejectHosting();
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public IpInfo lookup(ClientIp ip) {
        Optional<IpInfo> cached = cache.get(ip);
        if (cached.isPresent()) {
            return cached.get();
        }
        failIfBlocked();
        IpInfo info = toIpInfo(call(ip));
        cache.put(ip, info);
        return info;
    }

    private void failIfBlocked() {
        Instant now = clock.instant();
        Instant until = blockedUntil.get();
        if (now.isBefore(until)) {
            throw unavailable("Geolocation provider rate limit reached.", Duration.between(now, until));
        }
    }

    private IpApiResponse call(ClientIp ip) {
        try {
            return http.get().uri(PATH, ip.value()).exchange((request, response) -> {
                rememberRateLimit(response.getHeaders());
                if (response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    Duration block = ttl(response.getHeaders()).orElse(DEFAULT_BLOCK);
                    blockedUntil.set(clock.instant().plus(block));
                    throw unavailable("Geolocation provider rate limit reached.", block);
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw unavailable("Geolocation provider error " + response.getStatusCode().value() + ".",
                            DEFAULT_RETRY_AFTER);
                }
                return response.bodyTo(IpApiResponse.class);
            });
        } catch (RestClientException e) {
            throw unavailable("Geolocation provider unreachable.", DEFAULT_RETRY_AFTER);
        }
    }

    private void rememberRateLimit(HttpHeaders headers) {
        String remaining = headers.getFirst("X-Rl");
        if ("0".equals(remaining)) {
            ttl(headers).ifPresent(ttl -> blockedUntil.set(clock.instant().plus(ttl)));
        }
    }

    private static Optional<Duration> ttl(HttpHeaders headers) {
        try {
            String ttl = headers.getFirst("X-Ttl");
            return ttl == null ? Optional.empty() : Optional.of(Duration.ofSeconds(Long.parseLong(ttl.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private IpInfo toIpInfo(IpApiResponse response) {
        if (response != null && !response.isSuccess() && response.message() != null
                && UNRESOLVABLE.contains(response.message())) {
            throw new CouponException(ErrorCode.INVALID_IP_ADDRESS,
                    "IP address cannot be geolocated: " + response.message() + ".");
        }
        if (response == null || !response.isSuccess() || response.countryCode() == null) {
            throw unavailable("Geolocation provider could not resolve the address.", DEFAULT_RETRY_AFTER);
        }
        String country = response.countryCode().toUpperCase(Locale.ROOT);
        if (!COUNTRY.matcher(country).matches()) {
            throw unavailable("Geolocation provider returned an invalid country.", DEFAULT_RETRY_AFTER);
        }
        boolean anonymized = Boolean.TRUE.equals(response.proxy())
                || (rejectHosting && Boolean.TRUE.equals(response.hosting()));
        return new IpInfo(country, anonymized);
    }

    private static CouponException unavailable(String message, Duration retryAfter) {
        return new CouponException(ErrorCode.GEOLOCATION_UNAVAILABLE, message, retryAfter);
    }
}
