package pl.kupony.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.kupony.application.archival.ArchivalProperties;
import pl.kupony.domain.port.out.ArchivalTaskPort;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ArchivalProperties.class)
class ArchivalConfig {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor archivalExecutor(ArchivalProperties properties) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                Thread.ofVirtual().name("archival-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    Gauge archivalPendingGauge(MeterRegistry registry, ArchivalTaskPort tasks) {
        return Gauge.builder("coupons.archival.pending", tasks, ArchivalTaskPort::countPending)
                .description("Archival tasks waiting to be processed")
                .register(registry);
    }

    @Bean
    Gauge archivalFailedGauge(MeterRegistry registry, ArchivalTaskPort tasks) {
        return Gauge.builder("coupons.archival.failed", tasks, ArchivalTaskPort::countFailed)
                .description("Archival tasks that exhausted their retries")
                .register(registry);
    }
}
