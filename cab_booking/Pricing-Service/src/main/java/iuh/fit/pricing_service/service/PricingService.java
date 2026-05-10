package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.config.KafkaConfig;
import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.exception.PricingException;
import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.model.FareEstimateRequest;
import iuh.fit.pricing_service.model.FareEstimateResponse;
import iuh.fit.pricing_service.model.SurgeRule;
import iuh.fit.pricing_service.producer.SurgeEventProducer;
import iuh.fit.pricing_service.repository.FareEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final FareEstimateRepository fareEstimateRepository;
    private final DistanceCalculatorService distanceCalculator;
    private final SurgePricingService surgePricingService;
    private final SurgeEventProducer surgeEventProducer;
    private final PricingConfigProperties pricingConfig;

    private static final String DEFAULT_CURRENCY = "USD";
    private static final int ESTIMATE_EXPIRY_MINUTES = 15;

    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request) {
        log.info("Calculating fare estimate for vehicle type: {}, from ({}, {}) to ({}, {})",
                request.getVehicleType(),
                request.getPickupLat(), request.getPickupLng(),
                request.getDropoffLat(), request.getDropoffLng());

        String vehicleType = normalizeVehicleType(request.getVehicleType());

        double distance = distanceCalculator.calculateDistance(
                request.getPickupLat(), request.getPickupLng(),
                request.getDropoffLat(), request.getDropoffLng()
        );

        int duration = request.getEstimatedDurationMinutes() != null
                ? request.getEstimatedDurationMinutes()
                : distanceCalculator.estimateDurationMinutesByDistance(distance);

        String pickupZone = determineZone(request.getPickupLat(), request.getPickupLng());
        String dropoffZone = determineZone(request.getDropoffLat(), request.getDropoffLng());

        BigDecimal surgeMultiplier = surgePricingService.getSurgeMultiplier(pickupZone);
        log.debug("Surge multiplier for zone {}: {}", pickupZone, surgeMultiplier);

        PricingConfigProperties.VehicleConfig vehicleConfig = getVehicleConfig(vehicleType);

        BigDecimal baseFare = vehicleConfig.getBaseFare();
        BigDecimal distanceFare = vehicleConfig.getPerKm().multiply(BigDecimal.valueOf(distance));
        BigDecimal timeFare = vehicleConfig.getPerMinute().multiply(BigDecimal.valueOf(duration));

        BigDecimal subtotal = baseFare.add(distanceFare).add(timeFare);
        BigDecimal surgeAmount = subtotal.multiply(surgeMultiplier.subtract(BigDecimal.ONE));
        BigDecimal totalFare = subtotal.add(surgeAmount);

        totalFare = totalFare.max(pricingConfig.getCalculation().getMinimumFare());

        BigDecimal finalTotal = totalFare.setScale(2, RoundingMode.HALF_UP);

        String estimateId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(ESTIMATE_EXPIRY_MINUTES);

        FareEstimate fareEstimate = FareEstimate.builder()
                .id(estimateId)
                .pickupZone(pickupZone)
                .dropoffZone(dropoffZone)
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropoffLat(request.getDropoffLat())
                .dropoffLng(request.getDropoffLng())
                .vehicleType(vehicleType)
                .distanceKm(distance)
                .durationMinutes(duration)
                .baseFare(baseFare.setScale(2, RoundingMode.HALF_UP))
                .distanceFare(distanceFare.setScale(2, RoundingMode.HALF_UP))
                .timeFare(timeFare.setScale(2, RoundingMode.HALF_UP))
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(finalTotal)
                .currency(DEFAULT_CURRENCY)
                .status(FareEstimate.EstimateStatus.PENDING.name())
                .createdAt(now)
                .expiresAt(expiresAt)
                .schemaVersion("1.0.0")
                .build();

        fareEstimateRepository.save(fareEstimate);
        log.info("Fare estimate saved: {} - Total fare: {} {}", estimateId, finalTotal, DEFAULT_CURRENCY);

        return FareEstimateResponse.fromFareEstimate(fareEstimate);
    }

    public FareEstimate confirmFare(String estimateId) {
        log.info("Confirming fare estimate: {}", estimateId);

        FareEstimate estimate = fareEstimateRepository.findById(estimateId)
                .orElseThrow(() -> new PricingException("Fare estimate not found: " + estimateId, "ESTIMATE_NOT_FOUND"));

        if (FareEstimate.EstimateStatus.EXPIRED.name().equals(estimate.getStatus())) {
            throw new PricingException("Fare estimate has expired: " + estimateId, "ESTIMATE_EXPIRED");
        }

        if (!FareEstimate.EstimateStatus.PENDING.name().equals(estimate.getStatus())) {
            throw new PricingException("Fare estimate is not in PENDING status: " + estimateId, "INVALID_STATUS");
        }

        estimate.setStatus(FareEstimate.EstimateStatus.CONFIRMED.name());
        FareEstimate confirmed = fareEstimateRepository.save(estimate);

        log.info("Fare confirmed for estimate {}: total fare = {} {}",
                estimateId, confirmed.getTotalFare(), confirmed.getCurrency());

        return confirmed;
    }

    public FareEstimate applyFinalPricing(String rideId, String pickupZone, String dropoffZone,
                                          String vehicleType, double distance, int duration) {
        log.info("Applying final pricing for ride {} - zone: {}, vehicle: {}, distance: {} km, duration: {} min",
                rideId, pickupZone, vehicleType, distance, duration);

        String normalizedVehicleType = normalizeVehicleType(vehicleType);

        BigDecimal surgeMultiplier = surgePricingService.getSurgeMultiplier(pickupZone);

        PricingConfigProperties.VehicleConfig vehicleConfig = getVehicleConfig(normalizedVehicleType);

        BigDecimal baseFare = vehicleConfig.getBaseFare();
        BigDecimal distanceFare = vehicleConfig.getPerKm().multiply(BigDecimal.valueOf(distance));
        BigDecimal timeFare = vehicleConfig.getPerMinute().multiply(BigDecimal.valueOf(duration));

        BigDecimal subtotal = baseFare.add(distanceFare).add(timeFare);
        BigDecimal surgeAmount = subtotal.multiply(surgeMultiplier.subtract(BigDecimal.ONE));
        BigDecimal totalFare = subtotal.add(surgeAmount).setScale(2, RoundingMode.HALF_UP);

        totalFare = totalFare.max(pricingConfig.getCalculation().getMinimumFare());

        LocalDateTime now = LocalDateTime.now();

        FareEstimate fare = FareEstimate.builder()
                .id(UUID.randomUUID().toString())
                .rideId(rideId)
                .pickupZone(pickupZone)
                .dropoffZone(dropoffZone)
                .vehicleType(normalizedVehicleType)
                .distanceKm(distance)
                .durationMinutes(duration)
                .baseFare(baseFare.setScale(2, RoundingMode.HALF_UP))
                .distanceFare(distanceFare.setScale(2, RoundingMode.HALF_UP))
                .timeFare(timeFare.setScale(2, RoundingMode.HALF_UP))
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(totalFare)
                .currency(DEFAULT_CURRENCY)
                .status(FareEstimate.EstimateStatus.CONFIRMED.name())
                .createdAt(now)
                .schemaVersion("1.0.0")
                .build();

        FareEstimate saved = fareEstimateRepository.save(fare);
        log.info("Final pricing applied for ride {}: fare = {} {}", rideId, totalFare, DEFAULT_CURRENCY);

        return saved;
    }

    public void updateSurgeForZone(String zoneId, BigDecimal surgeMultiplier) {
        log.info("Updating surge for zone {}: new multiplier = {}", zoneId, surgeMultiplier);

        if (surgePricingService.shouldUpdateSurge(zoneId, surgeMultiplier)) {
            String source = SurgeRule.SurgeSource.AUTOMATIC.name();
            surgePricingService.createOrUpdateSurgeRule(zoneId, surgeMultiplier, source);

            surgeEventProducer.publishSurgeUpdate(zoneId, surgeMultiplier);

            log.info("Surge updated and event published for zone {}", zoneId);
        } else {
            log.debug("Surge change below threshold for zone {}, no update needed", zoneId);
        }
    }

    public void processDemandSupplyUpdate(String zoneId, int activeDrivers, int pendingRides) {
        log.debug("Processing demand/supply update for zone {}: drivers={}, rides={}",
                zoneId, activeDrivers, pendingRides);

        BigDecimal newSurge = surgePricingService.calculateSurgeBasedOnDemandSupply(
                zoneId, activeDrivers, pendingRides);

        updateSurgeForZone(zoneId, newSurge);
    }

    private String normalizeVehicleType(String vehicleType) {
        if (vehicleType == null || vehicleType.isBlank()) {
            return "ECONOMY";
        }
        return vehicleType.toUpperCase().trim();
    }

    private PricingConfigProperties.VehicleConfig getVehicleConfig(String vehicleType) {
        return pricingConfig.getVehicle().getOrDefault(
                vehicleType.toLowerCase(),
                new PricingConfigProperties.VehicleConfig()
        );
    }

    private String determineZone(double lat, double lng) {
        int gridSize = 1;
        int latZone = (int) Math.floor(lat * gridSize);
        int lngZone = (int) Math.floor(lng * gridSize);
        return String.format("Z%02d%02d", latZone + 50, lngZone + 100);
    }
}
