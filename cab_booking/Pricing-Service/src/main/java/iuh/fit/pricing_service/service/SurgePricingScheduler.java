package iuh.fit.pricing_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgePricingScheduler {

    private final SurgePricingService surgePricingService;

    private static final int FIXED_RATE_MS = 60000;

    @Scheduled(fixedRateString = "${pricing.surge.cache-ttl-seconds:60}000")
    public void refreshSurgeCache() {
        log.debug("Starting scheduled surge cache refresh...");

        try {
            Map<String, java.math.BigDecimal> allSurge = surgePricingService.getAllSurgeMultipliers();

            Set<String> zoneIds = allSurge.keySet();
            log.debug("Found {} zones with cached surge multipliers", zoneIds.size());

            for (String zoneId : zoneIds) {
                try {
                    java.math.BigDecimal currentSurge = allSurge.get(zoneId);
                    log.trace("Zone {} has surge multiplier: {}", zoneId, currentSurge);
                } catch (Exception e) {
                    log.warn("Error processing zone {} during cache refresh: {}", zoneId, e.getMessage());
                }
            }

            log.debug("Completed surge cache refresh for {} zones", zoneIds.size());
        } catch (Exception e) {
            log.error("Error during scheduled surge cache refresh: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void logSurgeStatistics() {
        log.info("=== Surge Pricing Statistics ===");

        try {
            Map<String, java.math.BigDecimal> allSurge = surgePricingService.getAllSurgeMultipliers();

            if (allSurge.isEmpty()) {
                log.info("No active surge zones found");
                return;
            }

            long highSurgeZones = allSurge.values().stream()
                    .filter(s -> s.compareTo(java.math.BigDecimal.ONE) > 0)
                    .count();

            java.math.BigDecimal maxSurge = allSurge.values().stream()
                    .max(java.math.BigDecimal::compareTo)
                    .orElse(java.math.BigDecimal.ONE);

            log.info("Total zones with surge data: {}", allSurge.size());
            log.info("Zones with active surge (>1.0x): {}", highSurgeZones);
            log.info("Maximum surge multiplier: {}x", maxSurge);

            if (maxSurge.compareTo(new java.math.BigDecimal("2.0")) > 0) {
                log.warn("High surge detected - Maximum surge is {}x", maxSurge);
            }
        } catch (Exception e) {
            log.error("Error generating surge statistics: {}", e.getMessage(), e);
        }
    }
}
