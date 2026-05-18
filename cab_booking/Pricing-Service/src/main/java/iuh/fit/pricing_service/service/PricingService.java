package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.client.MapboxClient;
import iuh.fit.pricing_service.client.OpenMeteoClient;
import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.exception.PricingException;
import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.model.FareEstimateRequest;
import iuh.fit.pricing_service.model.FareEstimateResponse;
import iuh.fit.pricing_service.model.PricingTestResponse;
import iuh.fit.pricing_service.model.SurgeRule;
import iuh.fit.pricing_service.repository.FareEstimateRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private static final int ESTIMATE_EXPIRY_MINUTES = 15;
    private static final String ROUTE_CACHE_PREFIX = "pricing:route:";
    private static final String WEATHER_CACHE_PREFIX = "pricing:weather:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "pricing:idempotency:estimate:";

    private final FareEstimateRepository fareEstimateRepository;
    private final SurgePricingService surgePricingService;
    private final PricingConfigProperties pricingConfig;
    private final MapboxClient mapboxClient;
    private final OpenMeteoClient openMeteoClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final PricingMetrics pricingMetrics;
    private final ZoneService zoneService;

    @Value("${mapbox.api-key}")
    private String mapboxApiKey;

    public FareEstimateResponse calculateFareEstimate(FareEstimateRequest request, String idempotencyKey) {
        pricingMetrics.recordEstimateRequest();
        FareEstimate cachedEstimate = findEstimateByIdempotencyKey(idempotencyKey);
        if (cachedEstimate != null) {
            return FareEstimateResponse.fromFareEstimate(cachedEstimate);
        }

        String vehicleType = normalizeVehicleType(request.getVehicleType());
        String pickupZone = zoneService.determineZone(request.getPickupLat(), request.getPickupLng());
        String dropoffZone = zoneService.determineZone(request.getDropoffLat(), request.getDropoffLng());

        RouteEstimate route = getRouteEstimate(request);
        if (request.getEstimatedDurationMinutes() != null) {
            route = new RouteEstimate(
                    route.distanceKm(),
                    request.getEstimatedDurationMinutes(),
                    route.source(),
                    route.fallbackUsed()
            );
        }

        WeatherContext weather = getWeatherContext(pickupZone, request.getPickupLat(), request.getPickupLng());
        BigDecimal surgeMultiplier = computeCurrentSurgeMultiplier(pickupZone, weather.badWeather());
        FareBreakdown fareBreakdown = calculateFare(vehicleType, route.distanceKm(), route.durationMinutes(), surgeMultiplier);

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
                .distanceKm(route.distanceKm())
                .durationMinutes(route.durationMinutes())
                .baseFare(fareBreakdown.baseFare())
                .distanceFare(fareBreakdown.distanceFare())
                .timeFare(fareBreakdown.timeFare())
                .platformFee(fareBreakdown.platformFee())
                .zoneFee(fareBreakdown.zoneFee())
                .airportFee(fareBreakdown.airportFee())
                .tollFee(fareBreakdown.tollFee())
                .discountAmount(fareBreakdown.discountAmount())
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(fareBreakdown.totalFare())
                .currency(pricingConfig.getCalculation().getCurrency())
                .pricingConfigVersion(pricingConfig.getCalculation().getConfigVersion())
                .distanceSource(route.source())
                .weatherCondition(weather.description())
                .weatherSource(weather.source())
                .fallbackUsed(route.fallbackUsed() || weather.fallbackUsed())
                .status(FareEstimate.EstimateStatus.PENDING.name())
                .createdAt(now)
                .expiresAt(now.plusMinutes(ESTIMATE_EXPIRY_MINUTES))
                .schemaVersion("1.0.0")
                .build();

        fareEstimateRepository.save(fareEstimate);
        rememberEstimateForIdempotency(idempotencyKey, estimateId);
        log.info("Fare estimate saved: {} - total fare: {} {}", estimateId,
                fareBreakdown.totalFare(), pricingConfig.getCalculation().getCurrency());

        return FareEstimateResponse.fromFareEstimate(fareEstimate);
    }

    public FareEstimate confirmFare(String estimateId) {
        LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where("_id").is(estimateId)
                .and("status").is(FareEstimate.EstimateStatus.PENDING.name())
                .and("expiresAt").gt(now));
        Update update = new Update().set("status", FareEstimate.EstimateStatus.CONFIRMED.name());

        FareEstimate confirmed = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                FareEstimate.class
        );
        if (confirmed == null) {
            FareEstimate existing = fareEstimateRepository.findById(estimateId)
                    .orElseThrow(() -> {
                        pricingMetrics.recordConfirmFailure("not_found");
                        return new PricingException("Fare estimate not found: " + estimateId, "ESTIMATE_NOT_FOUND");
                    });
            if (!FareEstimate.EstimateStatus.PENDING.name().equals(existing.getStatus())) {
                pricingMetrics.recordConfirmFailure("invalid_status");
                throw new PricingException("Fare estimate is not in PENDING status: " + estimateId, "INVALID_STATUS");
            }
            pricingMetrics.recordConfirmFailure("expired");
            throw new PricingException("Fare estimate has expired: " + estimateId, "ESTIMATE_EXPIRED");
        }

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
                .platformFee(fareBreakdown.platformFee())
                .zoneFee(fareBreakdown.zoneFee())
                .airportFee(fareBreakdown.airportFee())
                .tollFee(fareBreakdown.tollFee())
                .discountAmount(fareBreakdown.discountAmount())
                .surgeMultiplier(surgeMultiplier.setScale(2, RoundingMode.HALF_UP))
                .totalFare(fareBreakdown.totalFare())
                .currency(pricingConfig.getCalculation().getCurrency())
                .pricingConfigVersion(pricingConfig.getCalculation().getConfigVersion())
                .distanceSource("FINAL_RIDE_DISTANCE")
                .weatherSource("NOT_APPLIED")
                .fallbackUsed(false)
                .status(FareEstimate.EstimateStatus.CONFIRMED.name())
                .createdAt(LocalDateTime.now())
                .schemaVersion("1.0.0")
                .build();

        FareEstimate saved = fareEstimateRepository.save(fare);
        log.info("Final pricing applied for ride {}: fare = {} {}", rideId,
                fareBreakdown.totalFare(), pricingConfig.getCalculation().getCurrency());

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
            log.info("Manual surge updated for zone {}", zoneId);
        }
    }

    public void processDemandSupplyUpdate(String zoneId, int activeDrivers, int pendingRides) {
        surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
        log.info("Demand/supply metrics cached for zone {}.",                zoneId);
    }

    public Map<String, Object> testMapboxConnection() {
        try {
            String coordinates = "106.6297,10.8231;106.6601,10.7626";
            MapboxClient.MapboxMatrixResponse response = callMapbox(coordinates);

            Map<String, Object> result = new HashMap<>();
            result.put("success", "Ok".equalsIgnoreCase(response.code()));
            result.put("status", response.code() != null ? response.code() : "UNKNOWN");
            result.put("raw_response", response);
            result.put("api_key_used", maskApiKey(mapboxApiKey));
            return result;
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error_message", e.getMessage());
            errorResult.put("error_class", e.getClass().getName());
            errorResult.put("api_key_used", maskApiKey(mapboxApiKey));
            return errorResult;
        }
    }

    public PricingTestResponse calculateSimplePrice(Double distanceKm, Double demandIndex) {
        BigDecimal surgeMultiplier = BigDecimal.valueOf(demandIndex)
                .max(pricingConfig.getSurge().getMinMultiplier())
                .min(pricingConfig.getSurge().getMaxMultiplier());

        BigDecimal baseFare = pricingConfig.getCalculation().getBaseFare();
        BigDecimal distanceFare = pricingConfig.getCalculation().getPerKmRate().multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal subtotal = baseFare.add(distanceFare).add(pricingConfig.getCalculation().getPlatformFee());
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

    private RouteEstimate getRouteEstimate(FareEstimateRequest request) {
        String cacheKey = ROUTE_CACHE_PREFIX + routeHash(request);
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return new RouteEstimate(
                        toDouble(cached.get("distanceKm"), 0),
                        toInteger(cached.get("durationMinutes"), pricingConfig.getEta().getFallbackDurationMinutes()),
                        "MAPBOX_CACHE",
                        false
                );
            }
        } catch (Exception e) {
            log.warn("Route cache lookup failed: {}", e.getMessage());
        }

        try {
            String coordinates = request.getPickupLng() + "," + request.getPickupLat() + ";"
                    + request.getDropoffLng() + "," + request.getDropoffLat();
            MapboxClient.MapboxMatrixResponse response = callMapbox(coordinates);

            if (response == null || !"Ok".equalsIgnoreCase(response.code())
                    || response.distances() == null || response.durations() == null) {
                throw new PricingException("Invalid response from Mapbox API", "MAPS_API_ERROR");
            }

            RouteEstimate route = new RouteEstimate(
                    response.distances()[0][1] / 1000.0,
                    Math.max(1, (int) Math.round(response.durations()[0][1] / 60.0)),
                    "MAPBOX",
                    false
            );
            cacheRoute(cacheKey, route);
            return route;
        } catch (Exception e) {
            log.warn("Mapbox API failed. Using Haversine fallback. Error: {}", e.getMessage());
            pricingMetrics.recordFallback("mapbox");
            double distanceKm = calculateHaversineDistance(
                    request.getPickupLat(), request.getPickupLng(),
                    request.getDropoffLat(), request.getDropoffLng()
            ) * 1.3;
            int duration = (int) Math.max(1, Math.round((distanceKm / 30.0) * 60));
            return new RouteEstimate(distanceKm, duration, "HAVERSINE_FALLBACK", true);
        }
    }

    private MapboxClient.MapboxMatrixResponse callMapbox(String coordinates) {
        Instant start = Instant.now();
        try {
            return rateLimiterRegistry
                    .rateLimiter("mapbox")
                    .executeSupplier(() -> retryRegistry
                            .retry("mapbox")
                            .executeSupplier(() -> circuitBreakerRegistry
                                    .circuitBreaker("mapbox")
                                    .executeSupplier(() -> mapboxClient.getDistanceMatrix(
                                            coordinates, "distance,duration", mapboxApiKey))));
        } finally {
            pricingMetrics.recordExternalApiLatency("mapbox", Duration.between(start, Instant.now()));
        }
    }

    private void cacheRoute(String cacheKey, RouteEstimate route) {
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("distanceKm", route.distanceKm());
            values.put("durationMinutes", route.durationMinutes());
            redisTemplate.opsForHash().putAll(cacheKey, values);
            redisTemplate.expire(cacheKey, Duration.ofSeconds(pricingConfig.getCache().getRouteTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to cache route estimate: {}", e.getMessage());
        }
    }

    private WeatherContext getWeatherContext(String pickupZone, double lat, double lng) {
        String cacheKey = WEATHER_CACHE_PREFIX + pickupZone;
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                String description = String.valueOf(cached.getOrDefault("description", "Unknown"));
                boolean badWeather = Boolean.parseBoolean(String.valueOf(cached.getOrDefault("badWeather", "false")));
                return new WeatherContext(description, "OPEN_METEO_CACHE", badWeather, false);
            }
        } catch (Exception e) {
            log.warn("Weather cache lookup failed for zone {}: {}", pickupZone, e.getMessage());
        }

        try {
            OpenMeteoClient.OpenMeteoResponse weatherResponse = callOpenMeteo(lat, lng);
            if (weatherResponse != null && weatherResponse.current() != null) {
                String description = mapWeatherCodeToDescription(weatherResponse.current().weather_code());
                WeatherContext context = new WeatherContext(description, "OPEN_METEO", isBadWeather(description), false);
                cacheWeather(cacheKey, context);
                return context;
            }
        } catch (Exception e) {
            log.warn("Open-Meteo API failed. Weather surcharge disabled. Error: {}", e.getMessage());
            pricingMetrics.recordFallback("weather");
        }

        return new WeatherContext("Unavailable", "FALLBACK_UNAVAILABLE", false, true);
    }

    private OpenMeteoClient.OpenMeteoResponse callOpenMeteo(double lat, double lng) {
        Instant start = Instant.now();
        try {
            return rateLimiterRegistry
                    .rateLimiter("openMeteo")
                    .executeSupplier(() -> retryRegistry
                            .retry("openMeteo")
                            .executeSupplier(() -> circuitBreakerRegistry
                                    .circuitBreaker("openMeteo")
                                    .executeSupplier(() -> openMeteoClient.getForecast(
                                            lat, lng, "temperature_2m,weather_code"))));
        } finally {
            pricingMetrics.recordExternalApiLatency("openmeteo", Duration.between(start, Instant.now()));
        }
    }

    private void cacheWeather(String cacheKey, WeatherContext context) {
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("description", context.description());
            values.put("badWeather", context.badWeather());
            redisTemplate.opsForHash().putAll(cacheKey, values);
            redisTemplate.expire(cacheKey, Duration.ofSeconds(pricingConfig.getCache().getWeatherTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to cache weather context: {}", e.getMessage());
        }
    }

    private BigDecimal computeCurrentSurgeMultiplier(String pickupZone, boolean badWeather) {
        try {
            SurgePricingService.SurgeComputationResult result =
                    surgePricingService.computeSurgeFromRules(pickupZone, badWeather, LocalTime.now());
            return result.predictedMultiplier();
        } catch (Exception e) {
            log.warn("Rule-based surge computation failed for zone {}. Using cached/default surge. Error: {}",
                    pickupZone, e.getMessage());
            return surgePricingService.getSurgeMultiplier(pickupZone);
        }
    }

    private FareBreakdown calculateFare(String vehicleType, double distance, int duration, BigDecimal surgeMultiplier) {
        PricingConfigProperties.VehicleConfig vehicleConfig = getVehicleConfig(vehicleType);

        BigDecimal baseFare = vehicleConfig.getBaseFare();
        BigDecimal distanceFare = vehicleConfig.getPerKm().multiply(BigDecimal.valueOf(distance));
        BigDecimal timeFare = vehicleConfig.getPerMinute().multiply(BigDecimal.valueOf(duration));
        BigDecimal platformFee = pricingConfig.getCalculation().getPlatformFee();
        BigDecimal zoneFee = BigDecimal.ZERO;
        BigDecimal airportFee = pricingConfig.getCalculation().getAirportFee();
        BigDecimal tollFee = pricingConfig.getCalculation().getTollFee();
        BigDecimal discountAmount = BigDecimal.ZERO;

        BigDecimal subtotal = baseFare
                .add(distanceFare)
                .add(timeFare)
                .add(platformFee)
                .add(zoneFee)
                .add(airportFee)
                .add(tollFee);
        BigDecimal totalFare = subtotal.multiply(surgeMultiplier)
                .subtract(discountAmount)
                .max(pricingConfig.getCalculation().getMinimumFare())
                .setScale(2, RoundingMode.HALF_UP);
        if (surgeMultiplier.compareTo(BigDecimal.ONE) > 0) {
            pricingMetrics.recordSurgeApplied();
        }

        return new FareBreakdown(
                baseFare.setScale(2, RoundingMode.HALF_UP),
                distanceFare.setScale(2, RoundingMode.HALF_UP),
                timeFare.setScale(2, RoundingMode.HALF_UP),
                platformFee.setScale(2, RoundingMode.HALF_UP),
                zoneFee.setScale(2, RoundingMode.HALF_UP),
                airportFee.setScale(2, RoundingMode.HALF_UP),
                tollFee.setScale(2, RoundingMode.HALF_UP),
                discountAmount.setScale(2, RoundingMode.HALF_UP),
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

    private String routeHash(FareEstimateRequest request) {
        return String.format("%.4f:%.4f:%.4f:%.4f",
                request.getPickupLat(),
                request.getPickupLng(),
                request.getDropoffLat(),
                request.getDropoffLng());
    }

    private FareEstimate findEstimateByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        try {
            Object cachedEstimateId = redisTemplate.opsForValue().get(idempotencyKey(idempotencyKey));
            if (cachedEstimateId == null) {
                return null;
            }
            return fareEstimateRepository.findById(cachedEstimateId.toString())
                    .filter(estimate -> estimate.getExpiresAt() != null && estimate.getExpiresAt().isAfter(LocalDateTime.now()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve estimate idempotency key: {}", e.getMessage());
            return null;
        }
    }

    private void rememberEstimateForIdempotency(String idempotencyKey, String estimateId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    idempotencyKey(idempotencyKey),
                    estimateId,
                    Duration.ofMinutes(ESTIMATE_EXPIRY_MINUTES)
            );
        } catch (Exception e) {
            log.warn("Failed to cache estimate idempotency key: {}", e.getMessage());
        }
    }

    private String idempotencyKey(String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + idempotencyKey.trim();
    }

    private boolean isBadWeather(String weather) {
        return weather.contains("Rain")
                || weather.contains("Snow")
                || weather.contains("Thunderstorm")
                || weather.contains("Drizzle")
                || weather.contains("Fog");
    }

    private String maskApiKey(String apiKey) {
        return apiKey != null && apiKey.length() > 5 ? apiKey.substring(0, 5) + "..." : "EMPTY";
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private int toInteger(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
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

    private record RouteEstimate(
            double distanceKm,
            int durationMinutes,
            String source,
            boolean fallbackUsed
    ) {
    }

    private record WeatherContext(
            String description,
            String source,
            boolean badWeather,
            boolean fallbackUsed
    ) {
    }

    private record FareBreakdown(
            BigDecimal baseFare,
            BigDecimal distanceFare,
            BigDecimal timeFare,
            BigDecimal platformFee,
            BigDecimal zoneFee,
            BigDecimal airportFee,
            BigDecimal tollFee,
            BigDecimal discountAmount,
            BigDecimal totalFare
    ) {
    }
}
