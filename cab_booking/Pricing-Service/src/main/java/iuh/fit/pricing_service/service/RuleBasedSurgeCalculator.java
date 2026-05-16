package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.config.PricingConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class RuleBasedSurgeCalculator {

    private final PricingConfigProperties pricingConfig;

    public SurgeCalculation calculate(SurgeInput input) {
        PricingConfigProperties.Surge surgeConfig = pricingConfig.getSurge();

        BigDecimal demandAdjustment = calculateDemandAdjustment(input.activeDrivers(), input.pendingRides());
        BigDecimal timeAdjustment = calculateTimeAdjustment(input.requestTime());
        BigDecimal weatherAdjustment = input.badWeather()
                ? pricingConfig.getWeather().getBadWeatherAdjustment()
                : BigDecimal.ZERO;
        BigDecimal manualAdjustment = surgeConfig.getManualAdjustment();

        BigDecimal rawMultiplier = surgeConfig.getDefaultMultiplier()
                .add(demandAdjustment)
                .add(timeAdjustment)
                .add(weatherAdjustment)
                .add(manualAdjustment);

        BigDecimal finalMultiplier = rawMultiplier
                .max(surgeConfig.getMinMultiplier())
                .min(surgeConfig.getMaxMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        return new SurgeCalculation(
                finalMultiplier,
                demandAdjustment.setScale(2, RoundingMode.HALF_UP),
                timeAdjustment.setScale(2, RoundingMode.HALF_UP),
                weatherAdjustment.setScale(2, RoundingMode.HALF_UP),
                manualAdjustment.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal calculateDemandAdjustment(int activeDrivers, int pendingRides) {
        if (pendingRides <= 0) {
            return BigDecimal.ZERO;
        }

        if (activeDrivers <= 0) {
            return pricingConfig.getSurge().getVeryHighDemandAdjustment();
        }

        BigDecimal ratio = BigDecimal.valueOf(pendingRides)
                .divide(BigDecimal.valueOf(activeDrivers), 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(pricingConfig.getSurge().getVeryHighDemandRatio()) >= 0) {
            return pricingConfig.getSurge().getVeryHighDemandAdjustment();
        }
        if (ratio.compareTo(pricingConfig.getSurge().getHighDemandRatio()) >= 0) {
            return pricingConfig.getSurge().getHighDemandAdjustment();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateTimeAdjustment(LocalTime requestTime) {
        LocalTime time = requestTime == null ? LocalTime.now() : requestTime;
        if (isBetween(time, LocalTime.of(7, 0), LocalTime.of(9, 0))
                || isBetween(time, LocalTime.of(17, 0), LocalTime.of(19, 0))) {
            return pricingConfig.getSurge().getRushHourAdjustment();
        }
        if (!time.isBefore(LocalTime.of(23, 0)) || time.isBefore(LocalTime.of(5, 0))) {
            return pricingConfig.getSurge().getNightAdjustment();
        }
        return BigDecimal.ZERO;
    }

    private boolean isBetween(LocalTime value, LocalTime startInclusive, LocalTime endExclusive) {
        return !value.isBefore(startInclusive) && value.isBefore(endExclusive);
    }

    public record SurgeInput(
            String zoneId,
            int activeDrivers,
            int pendingRides,
            boolean badWeather,
            LocalTime requestTime
    ) {
    }

    public record SurgeCalculation(
            BigDecimal finalMultiplier,
            BigDecimal demandAdjustment,
            BigDecimal timeAdjustment,
            BigDecimal weatherAdjustment,
            BigDecimal manualAdjustment
    ) {
    }
}
