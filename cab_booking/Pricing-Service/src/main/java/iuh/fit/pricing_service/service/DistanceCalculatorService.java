package iuh.fit.pricing_service.service;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixElementStatus;
import com.google.maps.model.LatLng;
import com.google.maps.model.TrafficModel;
import com.google.maps.model.TravelMode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DistanceCalculatorService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double DEFAULT_AVERAGE_SPEED_KMH = 30.0;
    private static final int COORDINATE_SCALE = 4;
    private static final Duration ETA_CACHE_TTL = Duration.ofMinutes(3);
    private static final String ETA_CACHE_KEY_PREFIX = "pricing:eta:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final String googleMapsApiKey;
    private GeoApiContext geoApiContext;

    public DistanceCalculatorService() {
        this(null, null);
    }

    @Autowired
    public DistanceCalculatorService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${google.maps.api-key:}") String googleMapsApiKey
    ) {
        this.redisTemplate = redisTemplate;
        this.googleMapsApiKey = googleMapsApiKey;
    }

    @PostConstruct
    void initializeGeoApiContext() {
        if (!StringUtils.hasText(googleMapsApiKey)) {
            log.warn("Google Maps API key is not configured. ETA will use Haversine fallback.");
            return;
        }

        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(googleMapsApiKey)
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    void shutdownGeoApiContext() {
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
    }

    public EtaResult calculateEta(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        String cacheKey = buildEtaCacheKey(pickupLat, pickupLng, dropoffLat, dropoffLng);

        EtaResult cachedEta = getCachedEta(cacheKey);
        if (cachedEta != null) {
            log.debug("ETA cache hit for key {}", cacheKey);
            return cachedEta;
        }

        try {
            EtaResult googleEta = calculateEtaWithGoogleMaps(pickupLat, pickupLng, dropoffLat, dropoffLng);
            cacheEta(cacheKey, googleEta);
            return googleEta;
        } catch (Exception ex) {
            log.warn("Google Maps ETA lookup failed for key {}. Falling back to Haversine ETA. Cause: {}",
                    cacheKey, ex.getMessage());
            return calculateFallbackEta(pickupLat, pickupLng, dropoffLat, dropoffLng);
        }
    }

    public double calculateDistance(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        double dLat = Math.toRadians(dropoffLat - pickupLat);
        double dLng = Math.toRadians(dropoffLng - pickupLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(dropoffLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS_KM * c;
        log.debug("Calculated distance between ({}, {}) and ({}, {}): {} km",
                pickupLat, pickupLng, dropoffLat, dropoffLng, distance);

        return Math.round(distance * 100.0) / 100.0;
    }

    public int estimateDurationMinutes(double distanceKm, double averageSpeedKmh) {
        if (averageSpeedKmh <= 0) {
            averageSpeedKmh = 30.0;
        }
        int duration = (int) Math.ceil((distanceKm / averageSpeedKmh) * 60);
        log.debug("Estimated duration for {} km at {} km/h: {} minutes", distanceKm, averageSpeedKmh, duration);
        return Math.max(duration, 1);
    }

    public int estimateDurationMinutesByDistance(double distanceKm) {
        return estimateDurationMinutes(distanceKm, DEFAULT_AVERAGE_SPEED_KMH);
    }

    private EtaResult calculateEtaWithGoogleMaps(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng)
            throws IOException, InterruptedException, ApiException {
        if (geoApiContext == null) {
            throw new IllegalStateException("Google Maps GeoApiContext is not initialized");
        }

        DistanceMatrix matrix = DistanceMatrixApi.newRequest(geoApiContext)
                .origins(new LatLng(pickupLat, pickupLng))
                .destinations(new LatLng(dropoffLat, dropoffLng))
                .mode(TravelMode.DRIVING)
                .departureTime(Instant.now())
                .trafficModel(TrafficModel.BEST_GUESS)
                .await();

        DistanceMatrixElement element = extractFirstElement(matrix);
        long distanceMeters = element.distance.inMeters;
        long durationSeconds = element.durationInTraffic != null
                ? element.durationInTraffic.inSeconds
                : element.duration.inSeconds;

        double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;
        int durationMinutes = Math.max((int) Math.ceil(durationSeconds / 60.0), 1);

        log.debug("Google Maps ETA calculated: {} km, {} minutes", distanceKm, durationMinutes);
        return new EtaResult(distanceKm, durationMinutes, "GOOGLE_MAPS");
    }

    private DistanceMatrixElement extractFirstElement(DistanceMatrix matrix) {
        if (matrix == null || matrix.rows == null || matrix.rows.length == 0
                || matrix.rows[0].elements == null || matrix.rows[0].elements.length == 0) {
            throw new IllegalStateException("Google Maps returned an empty distance matrix");
        }

        DistanceMatrixElement element = matrix.rows[0].elements[0];
        if (element.status != DistanceMatrixElementStatus.OK || element.distance == null || element.duration == null) {
            throw new IllegalStateException("Google Maps returned invalid ETA status: " + element.status);
        }
        return element;
    }

    private EtaResult getCachedEta(String cacheKey) {
        if (redisTemplate == null) {
            return null;
        }

        try {
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue instanceof EtaResult) {
                EtaResult etaResult = (EtaResult) cachedValue;
                return etaResult;
            }
            if (cachedValue != null) {
                log.warn("Unexpected ETA cache value type for key {}: {}", cacheKey, cachedValue.getClass().getName());
            }
        } catch (Exception ex) {
            log.warn("Redis ETA cache read failed for key {}. Continuing without cache. Cause: {}",
                    cacheKey, ex.getMessage());
        }

        return null;
    }

    private void cacheEta(String cacheKey, EtaResult etaResult) {
        if (redisTemplate == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, etaResult, ETA_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Redis ETA cache write failed for key {}. Continuing without cache. Cause: {}",
                    cacheKey, ex.getMessage());
        }
    }

    private EtaResult calculateFallbackEta(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        double fallbackDistance = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
        int fallbackDuration = estimateDurationMinutesByDistance(fallbackDistance);
        return new EtaResult(fallbackDistance, fallbackDuration, "HAVERSINE_FALLBACK");
    }

    private String buildEtaCacheKey(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        return ETA_CACHE_KEY_PREFIX
                + roundedCoordinate(pickupLat) + "," + roundedCoordinate(pickupLng)
                + ":"
                + roundedCoordinate(dropoffLat) + "," + roundedCoordinate(dropoffLng);
    }

    private String roundedCoordinate(double coordinate) {
        return String.format(Locale.US, "%." + COORDINATE_SCALE + "f", coordinate);
    }

    public record EtaResult(double distanceKm, int durationMinutes, String source) {
    }
}
