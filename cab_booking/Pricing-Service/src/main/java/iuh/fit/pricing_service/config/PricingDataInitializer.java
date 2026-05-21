package iuh.fit.pricing_service.config;

import iuh.fit.pricing_service.model.PricingConfig;
import iuh.fit.pricing_service.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingDataInitializer implements ApplicationRunner {

    private final PricingConfigProperties pricingConfig;
    private final PricingConfigRepository pricingConfigRepository;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, PricingConfigProperties.VehicleConfig> vehicleMap = pricingConfig.getVehicle();
        if (vehicleMap == null || vehicleMap.isEmpty()) {
            log.warn("No vehicle configurations found in application.yaml");
            return;
        }

        for (Map.Entry<String, PricingConfigProperties.VehicleConfig> entry : vehicleMap.entrySet()) {
            String vehicleType = entry.getKey().toUpperCase();
            PricingConfigProperties.VehicleConfig vc = entry.getValue();

            if (pricingConfigRepository.findByVehicleType(vehicleType).isPresent()) {
                log.debug("PricingConfig for {} already exists in DB, skipping", vehicleType);
                continue;
            }

            PricingConfig config = PricingConfig.builder()
                    .vehicleType(vehicleType)
                    .baseFare(vc.getBaseFare().doubleValue())
                    .perKmRate(vc.getPerKm().doubleValue())
                    .perMinuteRate(vc.getPerMinute().doubleValue())
                    .multiplier(vc.getMultiplier() != null ? vc.getMultiplier().doubleValue() : 1.0)
                    .active(true)
                    .updatedAt(LocalDateTime.now())
                    .schemaVersion("1.0.0")
                    .build();

            pricingConfigRepository.save(config);
            log.info("Initialized PricingConfig for {} from application.yaml", vehicleType);
        }
    }
}
