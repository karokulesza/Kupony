package pl.kupony.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static pl.kupony.integration.StubIpIntelligence.PL_IP;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.kupony.MutableClock;
import pl.kupony.TestcontainersConfiguration;
import pl.kupony.adapter.out.persistence.repository.ActiveCouponRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivalTaskRepository;
import pl.kupony.adapter.out.persistence.repository.ArchivedRedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.RedemptionRepository;
import pl.kupony.adapter.out.persistence.repository.UsedCouponRepository;
import pl.kupony.application.archival.ArchivalService;
import pl.kupony.application.archival.ArchivalSweeper;
import pl.kupony.domain.port.out.ArchivalTaskPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"coupons.archival.batch-size=1000", "coupons.archival.batch-pause=0ms"})
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, StubIpIntelligence.class, ArchivalFixtures.class,
        TestClockConfiguration.class})
class ArchivalIT {

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner cleaner;

    @Autowired
    ArchivalFixtures fixtures;

    @Autowired
    ArchivalService archival;

    @Autowired
    ArchivalSweeper sweeper;

    @Autowired
    MutableClock clock;

    @MockitoSpyBean
    ArchivalTaskPort taskPort;

    @Autowired
    ActiveCouponRepository activeCoupons;

    @Autowired
    UsedCouponRepository usedCoupons;

    @Autowired
    RedemptionRepository redemptions;

    @Autowired
    ArchivedRedemptionRepository archivedRedemptions;

    @Autowired
    ArchivalTaskRepository tasks;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    void movesRedemptionsInBatchesThenTheCoupon() {
        var coupon = fixtures.exhaustedCoupon("WIOSNA", 2500, null);

        archival.archive(coupon.id());

        assertThat(activeCoupons.findById(coupon.id())).isEmpty();
        assertThat(redemptions.count()).isZero();
        assertThat(usedCoupons.findById(coupon.id())).hasValueSatisfying(c -> {
            assertThat(c.getUsedCount()).isEqualTo(2500);
            assertThat(c.getNormalizedCode()).isEqualTo("wiosna");
            assertThat(c.getArchivedAt()).isNotNull();
        });
        assertThat(archivedRedemptions.count()).isEqualTo(2500);
        assertThat(tasks.count()).isZero();
    }

    @Test
    void taskLeasedByAnotherWorkerIsLeftAloneUntilTheLeaseExpires() {
        var coupon = fixtures.exhaustedCoupon("LATO", 10, clock.instant().plus(Duration.ofMinutes(5)));

        archival.archive(coupon.id());
        assertThat(activeCoupons.findById(coupon.id())).isPresent();

        clock.advance(Duration.ofMinutes(6));
        archival.archive(coupon.id());
        assertThat(activeCoupons.findById(coupon.id())).isEmpty();
        assertThat(archivedRedemptions.count()).isEqualTo(10);
    }

    @Test
    void concurrentWorkersArchiveExactlyOnce() throws Exception {
        var coupon = fixtures.exhaustedCoupon("JESIEN", 1500, null);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> archival.archive(coupon.id()));
            executor.submit(() -> archival.archive(coupon.id()));
        }

        assertThat(usedCoupons.count()).isEqualTo(1);
        assertThat(archivedRedemptions.count()).isEqualTo(1500);
        assertThat(redemptions.count()).isZero();
        assertThat(tasks.count()).isZero();
    }

    @Test
    void repeatedFailureMarksTaskFailedAfterMaxAttempts() {
        var coupon = fixtures.exhaustedCoupon("ZIMA", 3, null);
        doThrow(new IllegalStateException("disk full"))
                .when(taskPort).moveRedemptionBatch(any(UUID.class), anyInt(), any());

        for (int attempt = 1; attempt <= 5; attempt++) {
            archival.archive(coupon.id());
            clock.advance(Duration.ofMinutes(2));
        }

        assertThat(tasks.findById(coupon.id())).hasValueSatisfying(t -> {
            assertThat(t.getStatus()).isEqualTo("FAILED");
            assertThat(t.getAttempts()).isEqualTo(5);
            assertThat(t.getLastError()).contains("disk full");
        });
        assertThat(taskPort.countFailed()).isEqualTo(1);
        assertThat(activeCoupons.findById(coupon.id())).isPresent();
        doCallRealMethod().when(taskPort).moveRedemptionBatch(any(UUID.class), anyInt(), any());
    }

    @Test
    void lastRedemptionThroughTheApiArchivesTheCouponAfterCommit() throws Exception {
        var api = new ApiClient(port);
        api.createCoupon("FLASH", "PL", 2);
        api.redeem("FLASH", UUID.randomUUID(), PL_IP);
        api.redeem("FLASH", UUID.randomUUID(), PL_IP);

        awaitTrue(() -> usedCoupons.count() == 1);
        assertThat(activeCoupons.count()).isZero();
        assertThat(archivedRedemptions.count()).isEqualTo(2);

        var late = api.redeem("FLASH", UUID.randomUUID(), PL_IP);
        assertThat(late.errorCode()).isEqualTo("COUPON_EXHAUSTED");
    }

    @Test
    void sweeperPicksUpOrphanedTasks() throws Exception {
        var coupon = fixtures.exhaustedCoupon("SIEROTA", 5, null);
        assertThat(taskPort.countPending()).isEqualTo(1);

        sweeper.sweep();

        awaitTrue(() -> usedCoupons.existsById(coupon.id()));
        assertThat(taskPort.countPending()).isZero();
    }

    @Test
    void oneSweepDrainsMoreThanAHundredOrphanedTasks() throws Exception {
        for (int i = 0; i < 150; i++) {
            fixtures.exhaustedCoupon("SIEROTA" + i, 1, null);
        }

        sweeper.sweep();

        awaitTrue(() -> usedCoupons.count() == 150);
        assertThat(taskPort.countPending()).isZero();
    }

    @Test
    void releasedLeasesDoNotConsumeAttempts() {
        var coupon = fixtures.exhaustedCoupon("WIOSNA", 1, null);
        for (int i = 0; i < 3; i++) {
            assertThat(taskPort.tryLease(coupon.id(), clock.instant().plus(Duration.ofMinutes(5)))).isTrue();
            taskPort.releaseLease(coupon.id());
        }

        taskPort.markFailure(coupon.id(), "transient", 2, clock.instant());

        assertThat(tasks.findById(coupon.id())).hasValueSatisfying(t -> {
            assertThat(t.getStatus()).isEqualTo("PENDING");
            assertThat(t.getAttempts()).isEqualTo(1);
        });
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 10s");
            }
            Thread.sleep(100);
        }
    }
}
