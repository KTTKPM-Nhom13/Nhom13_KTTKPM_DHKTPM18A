package iuh.fit.pricing_service.service;

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

import iuh.fit.pricing_service.client.MapboxClient;
import iuh.fit.pricing_service.client.OpenMeteoClient;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final FareEstimateRepository fareEstimateRepository;
    private final SurgePricingService surgePricingService;
    private final SurgeEventProducer surgeEventProducer;
    private final PricingConfigProperties pricingConfig;
    private final MapboxClient mapboxClient;
    private final OpenMeteoClient openMeteoClient;

    @Value("${mapbox.api-key}")
    private String mapboxApiKey;

    private static final String DEFAULT_CURRENCY = "USD";
    private static final int ESTIMATE_EXPIRY_MINUTES = 15;

    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request) {
        String vehicleType = normalizeVehicleType(request.getVehicleType());
        String pickupZone = determineZone(request.getPickupLat(), request.getPickupLng());
        String dropoffZone = determineZone(request.getDropoffLat(), request.getDropoffLng());

        double distanceKm = -1;
        int duration = -1;

        try {
            String coordinates = request.getPickupLng() + "," + request.getPickupLat() + ";"
                    + request.getDropoffLng() + "," + request.getDropoffLat();
            
            MapboxClient.MapboxMatrixResponse response = mapboxClient.getDistanceMatrix(
                    coordinates, "distance,duration", mapboxApiKey);
            
            if (response != null && "Ok".equalsIgnoreCase(response.code()) && response.distances() != null 
                && response.durations() != null) {
                
                distanceKm = response.distances()[0][1] / 1000.0;
                duration = (int) (response.durations()[0][1] / 60.0);
            } else {
                throw new PricingException("Invalid response from Mapbox API: " + (response != null ? response.code() : "null"), "MAPS_API_ERROR");
            }
        } catch (Exception e) {
            log.warn("Mapbox API failed. Using Haversine formula (đường chim bay) for fallback. Error: {}", e.getMessage());
            double straightLineDistance = calculateHaversineDistance(
                    request.getPickupLat(), request.getPickupLng(), 
                    request.getDropoffLat(), request.getDropoffLng()
            );
            // Nhân hệ số 1.3 để ước lượng quãng đường thực tế
            distanceKm = straightLineDistance * 1.3; 
            // Giả định tốc độ trung bình 30 km/h trong nội thành
            duration = (int) Math.max(1, Math.round((distanceKm / 30.0) * 60)); 
        }

        if (request.getEstimatedDurationMinutes() != null) {
            duration = request.getEstimatedDurationMinutes();
        }

        BigDecimal surgeMultiplier = surgePricingService.getSurgeMultiplier(pickupZone);
        String weather = "Clear";
        try {
            OpenMeteoClient.OpenMeteoResponse weatherResponse = openMeteoClient.getForecast(
                    request.getPickupLat(), request.getPickupLng(), "temperature_2m,weather_code");
            if (weatherResponse != null && weatherResponse.current() != null) {
                weather = mapWeatherCodeToDescription(weatherResponse.current().weather_code());
                log.info("Live weather retrieved: {} (Code: {})", weather, weatherResponse.current().weather_code());
                
                // Adjust surge multiplier based on bad weather conditions
                if (weather.contains("Rain") || weather.contains("Snow") || weather.contains("Thunderstorm") || weather.contains("Drizzle") || weather.contains("Fog")) {
                    surgeMultiplier = surgeMultiplier.multiply(BigDecimal.valueOf(1.2)); // 20% weather surge
                }
            }
        } catch (Exception e) {
            log.warn("Open-Meteo API failed, continuing with base surge multiplier. Error: {}", e.getMessage());
        }

        FareBreakdown fareBreakdown = calculateFare(vehicleType, distanceKm, duration, surgeMultiplier);

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
                .distanceKm(distanceKm)
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

    public java.util.Map<String, Object> testMapboxConnection() {
        try {
            String coordinates = "106.6297,10.8231;106.6601,10.7626";
            
            MapboxClient.MapboxMatrixResponse response = mapboxClient.getDistanceMatrix(
                    coordinates, "distance,duration", mapboxApiKey);
                    
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", "Ok".equalsIgnoreCase(response.code()));
            result.put("status", response.code() != null ? response.code() : "UNKNOWN");
            result.put("raw_response", response);
            result.put("api_key_used", mapboxApiKey != null && mapboxApiKey.length() > 5 
                    ? mapboxApiKey.substring(0, 5) + "..." : "EMPTY");
            
            return result;
        } catch (Exception e) {
            java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error_message", e.getMessage());
            errorResult.put("error_class", e.getClass().getName());
            errorResult.put("api_key_used", mapboxApiKey != null && mapboxApiKey.length() > 5 
                    ? mapboxApiKey.substring(0, 5) + "..." : "EMPTY");
            return errorResult;
        }
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

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Bán kính trái đất tính bằng km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String mapWeatherCodeToDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code >= 1 && code <= 3) return "Mainly clear, partly cloudy, and overcast";
        if (code >= 45 && code <= 48) return "Fog and depositing rime fog";
        if (code >= 51 && code <= 55) return "Drizzle: Light, moderate, and dense intensity";
        if (code >= 56 && code <= 57) return "Freezing Drizzle: Light and dense intensity";
        if (code >= 61 && code <= 65) return "Rain: Slight, moderate and heavy intensity";
        if (code >= 66 && code <= 67) return "Freezing Rain: Light and heavy intensity";
        if (code >= 71 && code <= 75) return "Snow fall: Slight, moderate, and heavy intensity";
        if (code == 77) return "Snow grains";
        if (code >= 80 && code <= 82) return "Rain showers: Slight, moderate, and violent";
        if (code >= 85 && code <= 86) return "Snow showers slight and heavy";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Unknown";
    }
}
