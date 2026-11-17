package pl.kupony.application.archival;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("coupons.archival")
public record ArchivalProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1000") int batchSize,
        @DefaultValue("50ms") Duration batchPause,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("1m") Duration retryBackoff,
        @DefaultValue("1000") int queueCapacity) {
}
