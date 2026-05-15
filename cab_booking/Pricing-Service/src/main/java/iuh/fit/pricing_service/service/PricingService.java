package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.client.EtaClient;
import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.exception.PricingException;
import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.model.FareEstimateRequest;
import iuh.fit.pricing_service.model.FareEstimateResponse;
import iuh.fit.pricing_service.model.PricingTestResponse;
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
    private final EtaClient etaClient;
    private final SurgePricingService surgePricingService;
    private final SurgeEventProducer surgeEventProducer;
    private final PricingConfigProperties pricingConfig;

    private static final String DEFAULT_CURRENCY = "USD";
    private static final int ESTIMATE_EXPIRY_MINUTES = 15;

    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request) {
        String vehicleType = normalizeVehicleType(request.getVehicleType());
        EtaClient.EtaEstimateResponse eta = fetchEta(request);
        int duration = request.getEstimatedDurationMinutes() != null
                ? request.getEstimatedDurationMinutes()
                : eta.durationMinutes();

        String pickupZone = determineZone(request.getPickupLat(), request.getPickupLng());
        String dropoffZone = determineZone(request.getDropoffLat(), request.getDropoffLng());
        BigDecimal surgeMultiplier = surgePricingService.getSurgeMultiplier(pickupZone);

        FareBreakdown fareBreakdown = calculateFare(vehicleType, eta.distanceKm(), duration, surgeMultiplier);

        String estimateId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        FareEstimate fareEstimate = FareEstimate.builder()
                .id(estimateId)
                .pickupZone(pickupZone)
                .dropoffZone(dropoffZone)
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropoffLat(request.getDropoffLat())
                .dropoffLng(request.getDropoffLng())
                .vehicleType(vehicleType)
                .distanceKm(eta.distanceKm())
                .durationMinutes(duration)
                .baseFare(fareBreakdown.baseFare())
                .distanceFare(fareBreakdown.distanceFare())
                .timeFare(fareBreakdown.timeFare())
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(fareBreakdown.totalFare())
                .currency(DEFAULT_CURRENCY)
                .status(FareEstimate.EstimateStatus.PENDING.name())
                .createdAt(now)
                .expiresAt(now.plusMinutes(ESTIMATE_EXPIRY_MINUTES))
                .schemaVersion("1.0.0")
                .build();

        fareEstimateRepository.save(fareEstimate);
        log.info("Fare estimate saved: {} - total fare: {} {}", estimateId,
                fareBreakdown.totalFare(), DEFAULT_CURRENCY);

        return FareEstimateResponse.fromFareEstimate(fareEstimate);
    }

    public FareEstimate confirmFare(String estimateId) {
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
        String normalizedVehicleType = normalizeVehicleType(vehicleType);
        BigDecimal surgeMultiplier = surgePricingService.getSurgeMultiplier(pickupZone);
        FareBreakdown fareBreakdown = calculateFare(normalizedVehicleType, distance, duration, surgeMultiplier);

        FareEstimate fare = FareEstimate.builder()
                .id(UUID.randomUUID().toString())
                .rideId(rideId)
                .pickupZone(pickupZone)
                .dropoffZone(dropoffZone)
                .vehicleType(normalizedVehicleType)
                .distanceKm(distance)
                .durationMinutes(duration)
                .baseFare(fareBreakdown.baseFare())
                .distanceFare(fareBreakdown.distanceFare())
                .timeFare(fareBreakdown.timeFare())
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(fareBreakdown.totalFare())
                .currency(DEFAULT_CURRENCY)
                .status(FareEstimate.EstimateStatus.CONFIRMED.name())
                .createdAt(LocalDateTime.now())
                .schemaVersion("1.0.0")
                .build();

        FareEstimate saved = fareEstimateRepository.save(fare);
        log.info("Final pricing applied for ride {}: fare = {} {}", rideId,
                fareBreakdown.totalFare(), DEFAULT_CURRENCY);

        return saved;
    }

    public void updateSurgeForZone(String zoneId, BigDecimal surgeMultiplier) {
        BigDecimal normalizedMultiplier = surgeMultiplier.setScale(2, RoundingMode.HALF_UP);
        if (surgePricingService.shouldUpdateSurge(zoneId, normalizedMultiplier)) {
            surgePricingService.createOrUpdateSurgeRule(
                    zoneId,
                    normalizedMultiplier,
                    SurgeRule.SurgeSource.MANUAL.name()
            );
            surgeEventProducer.publishSurgeUpdate(zoneId, normalizedMultiplier);
            log.info("Manual surge updated and event published for zone {}", zoneId);
        }
    }

    public void processDemandSupplyUpdate(String zoneId, int activeDrivers, int pendingRides) {
        surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
        log.info("Demand/supply metrics cached for zone {}. Surge computation is handled by scheduler.",
                zoneId);
    }

    public PricingTestResponse calculateSimplePrice(Double distanceKm, Double demandIndex) {
        BigDecimal surgeMultiplier = BigDecimal.valueOf(demandIndex)
                .max(pricingConfig.getSurge().getMinMultiplier())
                .min(pricingConfig.getSurge().getMaxMultiplier());

        BigDecimal baseFare = pricingConfig.getCalculation().getBaseFare();
        BigDecimal distanceFare = pricingConfig.getCalculation().getPerKmRate().multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal subtotal = baseFare.add(distanceFare);
        BigDecimal totalFare = subtotal.multiply(surgeMultiplier)
                .max(pricingConfig.getCalculation().getMinimumFare())
                .setScale(2, RoundingMode.HALF_UP);

        return PricingTestResponse.builder()
                .distanceKm(distanceKm)
                .demandIndex(demandIndex)
                .baseFare(baseFare.setScale(2, RoundingMode.HALF_UP))
                .distanceFare(distanceFare.setScale(2, RoundingMode.HALF_UP))
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(totalFare)
                .message("Pricing calculated successfully")
                .build();
    }

    private EtaClient.EtaEstimateResponse fetchEta(FareEstimateRequest request) {
        try {
            EtaClient.EtaEstimateRequest etaRequest = new EtaClient.EtaEstimateRequest(
                    request.getPickupLat(),
                    request.getPickupLng(),
                    request.getDropoffLat(),
                    request.getDropoffLng()
            );
            EtaClient.EtaEstimateResponse response = etaClient.estimate(etaRequest);
            if (response == null || response.distanceKm() < 0 || response.durationMinutes() <= 0) {
                throw new PricingException("ETA Service returned invalid response", "ETA_INVALID_RESPONSE");
            }
            return response;
        } catch (PricingException e) {
            throw e;
        } catch (Exception e) {
            log.error("ETA Service call failed: {}", e.getMessage(), e);
            throw new PricingException("Unable to fetch ETA from ETA Service", "ETA_SERVICE_UNAVAILABLE");
        }
    }

    private FareBreakdown calculateFare(String vehicleType, double distance, int duration, BigDecimal surgeMultiplier) {
        PricingConfigProperties.VehicleConfig vehicleConfig = getVehicleConfig(vehicleType);

        BigDecimal baseFare = vehicleConfig.getBaseFare();
        BigDecimal distanceFare = vehicleConfig.getPerKm().multiply(BigDecimal.valueOf(distance));
        BigDecimal timeFare = vehicleConfig.getPerMinute().multiply(BigDecimal.valueOf(duration));
        BigDecimal subtotal = baseFare.add(distanceFare).add(timeFare);
        BigDecimal totalFare = subtotal.multiply(surgeMultiplier)
                .max(pricingConfig.getCalculation().getMinimumFare())
                .setScale(2, RoundingMode.HALF_UP);

        return new FareBreakdown(
                baseFare.setScale(2, RoundingMode.HALF_UP),
                distanceFare.setScale(2, RoundingMode.HALF_UP),
                timeFare.setScale(2, RoundingMode.HALF_UP),
                totalFare
        );
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

    private record FareBreakdown(
            BigDecimal baseFare,
            BigDecimal distanceFare,
            BigDecimal timeFare,
            BigDecimal totalFare
    ) {
    }
}
