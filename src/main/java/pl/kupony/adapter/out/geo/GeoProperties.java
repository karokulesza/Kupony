package pl.kupony.adapter.out.geo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("coupons.geo")
public record GeoProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        boolean rejectHosting,
        Duration cacheTtl,
        int cacheMaxSize) {
}
