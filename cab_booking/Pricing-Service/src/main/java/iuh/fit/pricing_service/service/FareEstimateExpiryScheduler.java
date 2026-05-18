package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.repository.FareEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FareEstimateExpiryScheduler {

    private final FareEstimateRepository fareEstimateRepository;

    @Scheduled(fixedDelayString = "${pricing.estimate.expiry-scheduler-fixed-delay-ms:60000}")
    public void expirePendingEstimates() {
        List<FareEstimate> expiredEstimates = fareEstimateRepository.findByStatusAndExpiresAtBefore(
                FareEstimate.EstimateStatus.PENDING.name(),
                LocalDateTime.now()
        );
        if (expiredEstimates.isEmpty()) {
            return;
        }

        for (FareEstimate estimate : expiredEstimates) {
            estimate.setStatus(FareEstimate.EstimateStatus.EXPIRED.name());
            fareEstimateRepository.save(estimate);
        }

        log.info("Expired {} pending fare estimates", expiredEstimates.size());
    }
}
