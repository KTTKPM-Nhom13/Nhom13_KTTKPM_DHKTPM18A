package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.client.SurgePricingAiClient;
import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.model.SurgeRule;
import iuh.fit.pricing_service.repository.SurgeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgePricingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PricingConfigProperties pricingConfig;
    private final SurgeRuleRepository surgeRuleRepository;
    private final SurgePricingAiClient surgePricingAiClient;

    private static final String SURGE_KEY_PREFIX = "pricing:surge:zone:";
    private static final String METRICS_KEY_PREFIX = "pricing:metrics:zone:";
    private static final String FEATURE_KEY_PREFIX = "pricing:features:zone:";
    private static final String ACTIVE_ZONES_KEY = "pricing:metrics:active-zones";

    public BigDecimal getSurgeMultiplier(String zoneId) {
        String cacheKey = surgeKey(zoneId);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return toBigDecimal(cached);
            }
        } catch (Exception e) {
            log.warn("Failed to get surge from Redis for zone {}. Falling back to DB. Cause: {}",
                    zoneId, e.getMessage());
        }

        Optional<SurgeRule> surgeRule = surgeRuleRepository.findByZoneId(zoneId);
        if (surgeRule.isPresent()) {
            BigDecimal multiplier = clampSurge(surgeRule.get().getSurgeMultiplier());
            cacheSurgeMultiplier(zoneId, multiplier);
            return multiplier;
        }

        return pricingConfig.getSurge().getDefaultMultiplier();
    }

    public void updateCurrentZoneMetrics(String zoneId, int activeDrivers, int pendingRides) {
        ZoneMetrics metrics = new ZoneMetrics(zoneId, activeDrivers, pendingRides, Instant.now());
        Duration ttl = Duration.ofSeconds(pricingConfig.getSurge().getMetricsTtlSeconds());

        try {
            Map<String, Object> values = new HashMap<>();
            values.put("zoneId", metrics.zoneId());
            values.put("activeDrivers", metrics.activeDrivers());
            values.put("pendingRides", metrics.pendingRides());
            values.put("updatedAt", metrics.updatedAt().toString());

            redisTemplate.opsForHash().putAll(metricsKey(zoneId), values);
            redisTemplate.expire(metricsKey(zoneId), ttl);
            redisTemplate.opsForSet().add(ACTIVE_ZONES_KEY, zoneId);
            log.debug("Updated current zone metrics in Redis: {}", metrics);
        } catch (Exception e) {
            log.warn("Failed to update current zone metrics for zone {}: {}", zoneId, e.getMessage());
        }
    }

    public Optional<ZoneMetrics> getCurrentZoneMetrics(String zoneId) {
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(metricsKey(zoneId));
            if (values == null || values.isEmpty()) {
                return Optional.empty();
            }

            int activeDrivers = toInteger(values.get("activeDrivers"), 0);
            int pendingRides = toInteger(values.get("pendingRides"), 0);
            Instant updatedAt = toInstant(values.get("updatedAt"));

            return Optional.of(new ZoneMetrics(zoneId, activeDrivers, pendingRides, updatedAt));
        } catch (Exception e) {
            log.warn("Failed to read current zone metrics for zone {}: {}", zoneId, e.getMessage());
            return Optional.empty();
        }
    }

    public Set<String> getZonesWithCurrentMetrics() {
        Set<String> zoneIds = new HashSet<>();
        try {
            Set<Object> members = redisTemplate.opsForSet().members(ACTIVE_ZONES_KEY);
            if (members != null) {
                for (Object member : members) {
                    if (member != null) {
                        zoneIds.add(member.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read active surge zones from Redis: {}", e.getMessage());
        }
        return zoneIds;
    }

    public SurgeComputationResult computeSurgeFromAi(String zoneId) {
        ZoneMetrics metrics = getCurrentZoneMetrics(zoneId)
                .orElseThrow(() -> new IllegalStateException("No current metrics found for zone " + zoneId));

        Map<String, Object> features = fetchHistoricalAndContextFeatures(zoneId);
        SurgePredictionRequest request = new SurgePredictionRequest(zoneId, metrics, features, Instant.now());
        BigDecimal predictedSurge = clampSurge(surgePricingAiClient.predictSurgeFactor(request));
        BigDecimal previousSurge = getSurgeMultiplier(zoneId);

        if (!shouldUpdateSurge(previousSurge, predictedSurge)) {
            return new SurgeComputationResult(zoneId, previousSurge, predictedSurge, false);
        }

        createOrUpdateSurgeRule(zoneId, predictedSurge, SurgeRule.SurgeSource.AUTOMATIC.name());
        return new SurgeComputationResult(zoneId, previousSurge, predictedSurge, true);
    }

    public Map<String, Object> fetchHistoricalAndContextFeatures(String zoneId) {
        Map<String, Object> features = new HashMap<>();
        try {
            Map<Object, Object> cachedFeatures = redisTemplate.opsForHash().entries(FEATURE_KEY_PREFIX + zoneId);
            if (cachedFeatures != null) {
                cachedFeatures.forEach((key, value) -> features.put(String.valueOf(key), value));
            }
        } catch (Exception e) {
            log.warn("FeatureStore lookup failed for zone {}. Continuing with current metrics only. Cause: {}",
                    zoneId, e.getMessage());
        }

        features.putIfAbsent("featureSource", "redis-feature-store");
        features.putIfAbsent("fetchedAt", Instant.now().toString());
        return features;
    }

    public boolean shouldUpdateSurge(String zoneId, BigDecimal newSurge) {
        return shouldUpdateSurge(getSurgeMultiplier(zoneId), newSurge);
    }

    public boolean shouldUpdateSurge(BigDecimal currentSurge, BigDecimal newSurge) {
        BigDecimal threshold = pricingConfig.getSurge().getUpdateThreshold();
        BigDecimal difference = newSurge.subtract(currentSurge).abs();
        return difference.compareTo(threshold) >= 0;
    }

    public SurgeRule createOrUpdateSurgeRule(String zoneId, BigDecimal surgeMultiplier, String source) {
        BigDecimal normalizedMultiplier = clampSurge(surgeMultiplier);
        SurgeRule surgeRule = surgeRuleRepository.findByZoneId(zoneId)
                .orElse(SurgeRule.builder()
                        .zoneId(zoneId)
                        .createdAt(LocalDateTime.now())
                        .build());

        surgeRule.setSurgeMultiplier(normalizedMultiplier);
        surgeRule.setLastUpdated(LocalDateTime.now());
        surgeRule.setSource(source);
        surgeRule.setSchemaVersion("1.0.0");

        SurgeRule savedRule = surgeRuleRepository.save(surgeRule);
        cacheSurgeMultiplier(zoneId, normalizedMultiplier);

        log.info("Updated surge rule for zone {}: multiplier={}, source={}",
                zoneId, normalizedMultiplier, source);
        return savedRule;
    }

    public void cacheSurgeMultiplier(String zoneId, BigDecimal multiplier) {
        try {
            redisTemplate.opsForValue().set(
                    surgeKey(zoneId),
                    clampSurge(multiplier).doubleValue(),
                    Duration.ofSeconds(pricingConfig.getSurge().getCacheTtlSeconds())
            );
        } catch (Exception e) {
            log.warn("Failed to cache surge multiplier for zone {}: {}", zoneId, e.getMessage());
        }
    }

    public Map<String, BigDecimal> getAllSurgeMultipliers() {
        Map<String, BigDecimal> allSurge = new HashMap<>();
        for (String zoneId : getZonesWithCurrentMetrics()) {
            allSurge.put(zoneId, getSurgeMultiplier(zoneId));
        }
        return allSurge;
    }

    public void invalidateSurgeCache(String zoneId) {
        try {
            redisTemplate.delete(surgeKey(zoneId));
        } catch (Exception e) {
            log.warn("Failed to invalidate surge cache for zone {}: {}", zoneId, e.getMessage());
        }
    }

    private BigDecimal clampSurge(BigDecimal multiplier) {
        if (multiplier == null) {
            return pricingConfig.getSurge().getDefaultMultiplier();
        }
        return multiplier
                .max(pricingConfig.getSurge().getMinMultiplier())
                .min(pricingConfig.getSurge().getMaxMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
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

    private Instant toInstant(Object value) {
        if (value == null) {
            return Instant.now();
        }
        return Instant.parse(value.toString());
    }

    private String surgeKey(String zoneId) {
        return SURGE_KEY_PREFIX + zoneId;
    }

    private String metricsKey(String zoneId) {
        return METRICS_KEY_PREFIX + zoneId;
    }

    public record ZoneMetrics(
            String zoneId,
            int activeDrivers,
            int pendingRides,
            Instant updatedAt
    ) {
    }

    public record SurgePredictionRequest(
            String zoneId,
            ZoneMetrics metrics,
            Map<String, Object> historicalAndContextFeatures,
            Instant requestedAt
    ) {
    }

    public record SurgeComputationResult(
            String zoneId,
            BigDecimal previousMultiplier,
            BigDecimal predictedMultiplier,
            boolean updated
    ) {
    }
}
