package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.model.SurgeRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurgePricingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PricingConfigProperties pricingConfig;
    private final iuh.fit.pricing_service.repository.SurgeRuleRepository surgeRuleRepository;

    private static final String SURGE_KEY_PREFIX = "surge:zone:";
    private static final String DEMAND_KEY_PREFIX = "demand:zone:";
    private static final String SUPPLY_KEY_PREFIX = "supply:zone:";

    public BigDecimal getSurgeMultiplier(String zoneId) {
        String cacheKey = SURGE_KEY_PREFIX + zoneId;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Retrieved surge multiplier from cache for zone {}: {}", zoneId, cached);
                if (cached instanceof Number) {
                    return BigDecimal.valueOf(((Number) cached).doubleValue());
                }
                return new BigDecimal(cached.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get surge from Redis, falling back to DB: {}", e.getMessage());
        }

        Optional<SurgeRule> surgeRule = surgeRuleRepository.findByZoneId(zoneId);
        if (surgeRule.isPresent()) {
            BigDecimal multiplier = surgeRule.get().getSurgeMultiplier();
            cacheSurgeMultiplier(zoneId, multiplier);
            return multiplier;
        }

        BigDecimal defaultMultiplier = pricingConfig.getSurge().getDefaultMultiplier();
        log.debug("No surge rule found for zone {}, using default multiplier: {}", zoneId, defaultMultiplier);
        return defaultMultiplier;
    }

    public void cacheSurgeMultiplier(String zoneId, BigDecimal multiplier) {
        String cacheKey = SURGE_KEY_PREFIX + zoneId;
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    multiplier.doubleValue(),
                    Duration.ofSeconds(pricingConfig.getSurge().getCacheTtlSeconds())
            );
            log.debug("Cached surge multiplier for zone {}: {}", zoneId, multiplier);
        } catch (Exception e) {
            log.warn("Failed to cache surge multiplier in Redis: {}", e.getMessage());
        }
    }

    public BigDecimal calculateSurgeBasedOnDemandSupply(String zoneId, int activeDrivers, int pendingRides) {
        if (activeDrivers == 0) {
            return pricingConfig.getSurge().getMaxMultiplier();
        }

        double ratio = (double) pendingRides / activeDrivers;
        BigDecimal calculatedSurge;

        if (ratio <= 0.5) {
            calculatedSurge = BigDecimal.ONE;
        } else if (ratio <= 1.0) {
            calculatedSurge = new BigDecimal("1.25");
        } else if (ratio <= 1.5) {
            calculatedSurge = new BigDecimal("1.5");
        } else if (ratio <= 2.0) {
            calculatedSurge = new BigDecimal("1.75");
        } else if (ratio <= 2.5) {
            calculatedSurge = new BigDecimal("2.0");
        } else if (ratio <= 3.0) {
            calculatedSurge = new BigDecimal("2.5");
        } else {
            calculatedSurge = pricingConfig.getSurge().getMaxMultiplier();
        }

        calculatedSurge = calculatedSurge.min(pricingConfig.getSurge().getMaxMultiplier());
        calculatedSurge = calculatedSurge.max(pricingConfig.getSurge().getMinMultiplier());

        log.info("Calculated surge for zone {} - drivers: {}, rides: {}, ratio: {:.2f}, surge: {}",
                zoneId, activeDrivers, pendingRides, ratio, calculatedSurge);

        updateSupplyDemandMetrics(zoneId, activeDrivers, pendingRides, calculatedSurge);

        return calculatedSurge;
    }

    private void updateSupplyDemandMetrics(String zoneId, int activeDrivers, int pendingRides, BigDecimal surge) {
        try {
            Duration ttl = Duration.ofSeconds(pricingConfig.getSurge().getCacheTtlSeconds());

            redisTemplate.opsForValue().set(SUPPLY_KEY_PREFIX + zoneId, activeDrivers, ttl);
            redisTemplate.opsForValue().set(DEMAND_KEY_PREFIX + zoneId, pendingRides, ttl);
            redisTemplate.opsForValue().set(SURGE_KEY_PREFIX + zoneId, surge.doubleValue(), ttl);

            log.debug("Updated Redis metrics for zone {}: drivers={}, rides={}, surge={}",
                    zoneId, activeDrivers, pendingRides, surge);
        } catch (Exception e) {
            log.warn("Failed to update Redis metrics: {}", e.getMessage());
        }
    }

    public boolean shouldUpdateSurge(String zoneId, BigDecimal newSurge) {
        BigDecimal currentSurge = getSurgeMultiplier(zoneId);
        BigDecimal threshold = pricingConfig.getSurge().getUpdateThreshold();
        BigDecimal difference = newSurge.subtract(currentSurge).abs();
        return difference.compareTo(threshold) > 0;
    }

    public Map<String, BigDecimal> getAllSurgeMultipliers() {
        Map<String, BigDecimal> allSurge = new HashMap<>();
        try {
            Set<String> keys = redisTemplate.keys(SURGE_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String zoneId = key.replace(SURGE_KEY_PREFIX, "");
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        if (value instanceof Number) {
                            allSurge.put(zoneId, BigDecimal.valueOf(((Number) value).doubleValue()));
                        } else {
                            allSurge.put(zoneId, new BigDecimal(value.toString()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get all surge multipliers from Redis: {}", e.getMessage());
        }
        return allSurge;
    }

    public void invalidateSurgeCache(String zoneId) {
        try {
            redisTemplate.delete(SURGE_KEY_PREFIX + zoneId);
            log.debug("Invalidated surge cache for zone: {}", zoneId);
        } catch (Exception e) {
            log.warn("Failed to invalidate surge cache: {}", e.getMessage());
        }
    }

    public SurgeRule createOrUpdateSurgeRule(String zoneId, BigDecimal surgeMultiplier, String source) {
        SurgeRule surgeRule = surgeRuleRepository.findByZoneId(zoneId)
                .orElse(SurgeRule.builder()
                        .zoneId(zoneId)
                        .createdAt(LocalDateTime.now())
                        .build());

        surgeRule.setSurgeMultiplier(surgeMultiplier);
        surgeRule.setLastUpdated(LocalDateTime.now());
        surgeRule.setSource(source);
        surgeRule.setSchemaVersion("1.0.0");

        SurgeRule savedRule = surgeRuleRepository.save(surgeRule);
        cacheSurgeMultiplier(zoneId, surgeMultiplier);

        log.info("Updated surge rule for zone {}: multiplier={}, source={}", zoneId, surgeMultiplier, source);
        return savedRule;
    }
}
