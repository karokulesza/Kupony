package pl.kupony.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class CouponApiIT {

    @LocalServerPort
    int port;

    @Autowired
    ActiveCouponRepository coupons;

    @Autowired
    DatabaseCleaner cleaner;

    ApiClient api;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        api = new ApiClient(port);
    }

    @Test
    void createsCouponAndStoresItAsActive() {
        var response = api.createCoupon("WIOSNA", "PL", 100);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json()).containsEntry("code", "WIOSNA").containsEntry("usedCount", 0);
        var stored = coupons.findAll();
        assertThat(stored).singleElement().satisfies(c -> {
            assertThat(c.getActiveCode()).isEqualTo("wiosna");
            assertThat(c.getNormalizedCode()).isEqualTo("wiosna");
            assertThat(c.isExhausted()).isFalse();
        });
    }

    @Test
    void secondActiveCouponWithSameCodeIsConflictIgnoringCase() {
        api.createCoupon("WIOSNA", "PL", 100);

        var upper = api.createCoupon("WIOSNA", "PL", 5);
        var lower = api.createCoupon("wiosna", "DE", 5);

        assertThat(upper.status()).isEqualTo(409);
        assertThat(upper.json()).containsEntry("errorCode", "COUPON_CODE_ALREADY_ACTIVE");
        assertThat(lower.status()).isEqualTo(409);
    }

    @Test
    void concurrentCreationOfSameCodeLetsExactlyOneWin() throws Exception {
        int threads = 20;
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return api.createCoupon("LATO", "PL", 10).status();
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (var r : results) {
            statuses.add(r.get());
        }
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
    }
}
