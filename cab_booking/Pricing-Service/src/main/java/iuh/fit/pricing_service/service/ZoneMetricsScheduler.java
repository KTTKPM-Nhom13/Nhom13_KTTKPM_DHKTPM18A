package iuh.fit.pricing_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ZoneMetricsScheduler {

    private final ZoneMetricsService zoneMetricsService;

    @Value("${services.zone-metrics.sync-fixed-delay-ms:30000}")
    private long syncFixedDelayMs;

    @Scheduled(fixedDelayString = "${services.zone-metrics.sync-fixed-delay-ms:30000}")
    public void syncAllZoneMetrics() {
        log.debug("Zone metrics scheduler triggered (interval={}ms)", syncFixedDelayMs);
        try {
            zoneMetricsService.syncAllActiveZones();
        } catch (Exception e) {
            log.error("Zone metrics scheduler failed: {}", e.getMessage(), e);
        }
    }
}
