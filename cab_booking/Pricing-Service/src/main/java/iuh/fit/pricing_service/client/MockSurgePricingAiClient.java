package iuh.fit.pricing_service.client;

import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.service.SurgePricingService.SurgePredictionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
@Slf4j
public class MockSurgePricingAiClient implements SurgePricingAiClient {

    private final PricingConfigProperties pricingConfig;

    @Override
    public BigDecimal predictSurgeFactor(SurgePredictionRequest request) {
        int activeDrivers = Math.max(request.metrics().activeDrivers(), 0);
        int pendingRides = Math.max(request.metrics().pendingRides(), 0);

        BigDecimal factor = pricingConfig.getSurge().getDefaultMultiplier();
        if (activeDrivers == 0 && pendingRides > 0) {
            factor = pricingConfig.getSurge().getMaxMultiplier();
        } else if (activeDrivers > 0) {
            double demandPressure = (double) pendingRides / activeDrivers;
            factor = BigDecimal.ONE.add(BigDecimal.valueOf(Math.max(0, demandPressure - 0.8) * 0.45));
        }

        factor = factor.max(pricingConfig.getSurge().getMinMultiplier())
                .min(pricingConfig.getSurge().getMaxMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Mock surge AI predicted factor {} for zone {}", factor, request.zoneId());
        return factor;
    }
}
