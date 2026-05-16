package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.producer.SurgeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgePricingScheduler {

    private final SurgePricingService surgePricingService;
    private final SurgeEventProducer surgeEventProducer;

    @Scheduled(fixedDelayString = "${pricing.surge.scheduler-fixed-delay-ms:60000}")
    public void refreshSurgePricing() {
        Set<String> zoneIds = surgePricingService.getZonesWithCurrentMetrics();
        if (zoneIds.isEmpty()) {
            log.debug("No zones with current demand/supply metrics. Skipping surge pricing cycle.");
            return;
        }

        log.info("Starting near real-time surge pricing cycle for {} zones", zoneIds.size());
        for (String zoneId : zoneIds) {
            try {
                SurgePricingService.SurgeComputationResult result = surgePricingService.computeSurgeFromRules(zoneId);
                if (result.updated()) {
                    surgeEventProducer.publishSurgeUpdate(zoneId, result.predictedMultiplier());
                    log.info("SurgePriceUpdated published for zone {}: {} -> {}",
                            zoneId, result.previousMultiplier(), result.predictedMultiplier());
                } else {
                    log.debug("Surge unchanged for zone {}: current={}, predicted={}",
                            zoneId, result.previousMultiplier(), result.predictedMultiplier());
                }
            } catch (Exception e) {
                log.warn("Surge pricing cycle failed for zone {}: {}", zoneId, e.getMessage(), e);
            }
        }
    }
}
