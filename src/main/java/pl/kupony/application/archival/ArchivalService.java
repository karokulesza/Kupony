package pl.kupony.application.archival;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.kupony.domain.port.out.ArchivalTaskPort;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArchivalService implements SmartLifecycle {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final ArchivalTaskPort tasks;
    @Qualifier("archivalExecutor")
    private final Executor executor;
    private final ArchivalProperties properties;
    private final Clock clock;
    private volatile boolean running;

    @TransactionalEventListener
    public void onCouponExhausted(CouponExhaustedEvent event) {
        submit(event.couponId());
    }

    public void submit(UUID couponId) {
        if (!properties.enabled()) {
            return;
        }
        try {
            executor.execute(() -> archive(couponId));
        } catch (RejectedExecutionException e) {
            log.debug("Archival queue full, coupon {} left for the sweeper", couponId);
        }
    }

    public void archive(UUID couponId) {
        if (!tasks.tryLease(couponId, clock.instant().plus(properties.lease()))) {
            return;
        }
        try {
            while (tasks.moveRedemptionBatch(couponId, properties.batchSize(),
                    clock.instant().plus(properties.lease())) > 0) {
                if (!running) {
                    tasks.releaseLease(couponId);
                    return;
                }
                pause();
            }
            tasks.finalizeCoupon(couponId);
            log.info("Coupon {} archived", couponId);
        } catch (RuntimeException e) {
            log.warn("Archival of coupon {} failed: {}", couponId, e.getMessage());
            tasks.markFailure(couponId, truncate(e.toString()), properties.maxAttempts(),
                    clock.instant().plus(properties.retryBackoff()));
        }
    }

    private void pause() {
        if (properties.batchPause().isZero()) {
            return;
        }
        try {
            Thread.sleep(properties.batchPause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
