package pl.kupony.application.archival;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.kupony.domain.port.out.ArchivalTaskPort;

@Component
@RequiredArgsConstructor
public class ArchivalSweeper {

    private final ArchivalTaskPort tasks;
    private final ArchivalService archival;
    private final ArchivalProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        sweep();
    }

    @Scheduled(fixedDelayString = "${coupons.archival.sweep-interval:PT1M}",
            initialDelayString = "${coupons.archival.sweep-interval:PT1M}")
    public void sweep() {
        tasks.findClaimable(properties.queueCapacity()).forEach(archival::submit);
    }
}
