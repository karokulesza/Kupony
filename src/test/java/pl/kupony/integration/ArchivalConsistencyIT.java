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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.repository.UsedCouponRepository;
import pl.kupony.application.archival.ArchivalService;

class ArchivalConsistencyIT {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"coupons.archival.enabled=false", "coupons.archival.batch-size=100",
                    "coupons.archival.batch-pause=20ms"})
    @Import({TestcontainersConfiguration.class, DatabaseCleaner.class, StubIpIntelligence.class,
            ArchivalFixtures.class})
    class RevealedReason extends Scenario {

        @Test
        void everyAttemptIsExhaustedWhileArchiving() throws Exception {
            assertSameReasonDuringArchival("COUPON_EXHAUSTED", 409);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"coupons.archival.enabled=false", "coupons.archival.batch-size=100",
                    "coupons.archival.batch-pause=20ms"})
    @TestPropertySource(properties = "coupons.reveal-exhausted-reason=false")
    @Import({TestcontainersConfiguration.class, DatabaseCleaner.class, StubIpIntelligence.class,
            ArchivalFixtures.class})
    class HiddenReason extends Scenario {

        @Test
        void everyAttemptIsNotFoundWhileArchiving() throws Exception {
            assertSameReasonDuringArchival("COUPON_NOT_FOUND", 404);
        }
    }

    abstract static class Scenario {

        @LocalServerPort
        int port;

        @Autowired
        DatabaseCleaner cleaner;

        @Autowired
        ArchivalFixtures fixtures;

        @Autowired
        ArchivalService archival;

        @Autowired
        UsedCouponRepository usedCoupons;

        @BeforeEach
        void setUp() {
            cleaner.clean();
        }

        void assertSameReasonDuringArchival(String expectedCode, int expectedStatus) throws Exception {
            var coupon = fixtures.exhaustedCoupon("ARCHIWUM", 5000, null);
            var api = new ApiClient(port);
            var start = new CountDownLatch(1);
            var inFlight = new Semaphore(100);
            List<Future<ApiClient.Response>> responses = new ArrayList<>();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.submit(() -> {
                    start.await();
                    archival.archive(coupon.id());
                    return null;
                });
                for (int i = 0; i < 2000; i++) {
                    UUID user = i % 10 == 0 ? coupon.users().get(i) : UUID.randomUUID();
                    responses.add(executor.submit(() -> {
                        start.await();
                        inFlight.acquire();
                        try {
                            return api.redeem("ARCHIWUM", user, PL_IP);
                        } finally {
                            inFlight.release();
                        }
                    }));
                }
                start.countDown();
            }

            for (var response : responses) {
                var r = response.get();
                assertThat(r.status()).isEqualTo(expectedStatus);
                assertThat(r.errorCode()).isEqualTo(expectedCode);
            }
            assertThat(usedCoupons.existsById(coupon.id())).as("archival really ran during the test").isTrue();
        }
    }
}
