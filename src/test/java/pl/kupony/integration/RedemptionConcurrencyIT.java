package pl.kupony.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.kupony.integration.StubIpIntelligence.PL_IP;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "coupons.archival.enabled=false")
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, StubIpIntelligence.class})
class RedemptionConcurrencyIT {

    private static final int MAX_IN_FLIGHT_BELOW_TOMCAT_ACCEPT_BACKLOG = 80;

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner cleaner;

    @Autowired
    ActiveCouponRepository coupons;

    @Autowired
    RedemptionRepository redemptions;

    ApiClient api;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        api = new ApiClient(port);
    }

    @RepeatedTest(3)
    void thousandConcurrentUsersNeverExceedTheLimit() throws Exception {
        api.createCoupon("FLASH", "PL", 100);
        UUID[] users = new UUID[1000];
        for (int i = 0; i < users.length; i++) {
            users[i] = UUID.randomUUID();
        }

        List<ApiClient.Response> responses = stampede(1000, i -> api.redeem("FLASH", users[i], PL_IP));

        long created = responses.stream().filter(r -> r.status() == 201).count();
        assertThat(created).isEqualTo(100);
        assertThat(responses).filteredOn(r -> r.status() != 201)
                .allSatisfy(r -> assertThat(r.errorCode()).isIn("COUPON_EXHAUSTED", "COUPON_BUSY"));
        assertThat(coupons.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getUsedCount()).isEqualTo(100);
            assertThat(c.isExhausted()).isTrue();
        });
        assertThat(redemptions.count()).isEqualTo(100);
    }

    @RepeatedTest(3)
    void sameUserRacingWithHimselfRedeemsOnce() throws Exception {
        api.createCoupon("SOLO", "PL", 10);
        UUID user = UUID.randomUUID();

        List<ApiClient.Response> responses = stampede(50, i -> api.redeem("SOLO", user, PL_IP));

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() != 201)
                .allSatisfy(r -> assertThat(r.errorCode()).isIn("COUPON_ALREADY_REDEEMED", "COUPON_BUSY"));
        assertThat(coupons.findAll()).singleElement().satisfies(c -> assertThat(c.getUsedCount()).isEqualTo(1));
    }

    private static List<ApiClient.Response> stampede(int count, IntFunction<ApiClient.Response> call)
            throws Exception {
        var start = new CountDownLatch(1);
        var inFlight = new Semaphore(MAX_IN_FLIGHT_BELOW_TOMCAT_ACCEPT_BACKLOG);
        List<Future<ApiClient.Response>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    inFlight.acquire();
                    try {
                        return call.apply(index);
                    } finally {
                        inFlight.release();
                    }
                }));
            }
            start.countDown();
        }
        List<ApiClient.Response> responses = new ArrayList<>();
        for (var f : futures) {
            responses.add(f.get());
        }
        return responses;
    }
}
