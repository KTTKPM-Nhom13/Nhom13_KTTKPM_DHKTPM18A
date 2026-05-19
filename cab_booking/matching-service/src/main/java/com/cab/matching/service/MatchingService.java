package com.cab.matching.service;

import com.cab.matching.client.AiScoringClient;
import com.cab.matching.client.AiScoringResponse;
import com.cab.matching.client.DriverFeatureDto;
import com.cab.matching.client.RankingEntry;
import com.cab.matching.core.dto.event.inbound.DriverRejectedEvent;
import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.core.dto.event.outbound.RideAssignedEvent;
import com.cab.matching.core.enums.VehicleType;
import com.cab.matching.core.enums.VehicleTypeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final String DRIVER_LOCATION_KEY = "driver:locations";
    private static final String DRIVER_STATUS_PREFIX = "driver:status:";
    private static final String DRIVER_VEHICLE_TYPE_PREFIX = "driver:vehicleType:";
    private static final String DRIVER_PROFILE_PREFIX = "driver:profile:";
    private static final String DRIVER_LOCK_PREFIX = "driver:lock:";
    private static final String MATCHING_REQUEST_PREFIX = "matching:request:";
    private static final String DRIVER_STATUS_AVAILABLE = "AVAILABLE";
    private static final String DRIVER_LOCK_VALUE = "LOCKED";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";
    private static final long LOCK_TTL_SECONDS = 60L;
    private static final int MAX_MATCHING_ATTEMPTS = 3;
    private static final String TOPIC_DRIVER_ASSIGNED = "driver.assigned";
    private static final String TOPIC_RIDE_ASSIGNED_LEGACY = "ride.assigned";
    private static final String TOPIC_BOOKING_CREATED = "booking.created";
    private static final String TOPIC_RIDE_CREATED_LEGACY = "ride.created";

    private final RedisTemplate<String, String> redisTemplate;
    private final AiScoringClient aiScoringClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final Random random = new Random();

    public void processMatching(RideCreatedEvent event) {
        cacheMatchingRequest(event);
        if (isBookingCancelled(event.rideId())) {
            log.info("Skip matching cancelled rideId={}", event.rideId());
            return;
        }

        VehicleType requestedVehicleType;
        try {
            requestedVehicleType = VehicleTypeNormalizer.normalizeVehicleType(event.vehicleType());
        } catch (IllegalArgumentException ex) {
            log.warn("Skip matching event with invalid vehicleType | rideId={} | rawVehicleType={} | reason={}",
                    event.rideId(), event.vehicleType(), ex.getMessage());
            return;
        }

        log.info("Start matching | rideId={} | vehicleType={}", event.rideId(), requestedVehicleType);

        List<DriverFeatureDto> candidates = fetchCandidatesFromRedis(
                event.pickupLat(),
                event.pickupLng(),
                event.searchRadiusKmOrDefault(),
                requestedVehicleType,
                event.excludedDriverIds());

        if (candidates.isEmpty()) {
            log.warn("No available driver candidates | rideId={} | vehicleType={}", event.rideId(), requestedVehicleType);
            scheduleRetryIfPossible(event);
            return;
        }

        List<RankingEntry> ranking = callAiWithFallback(candidates, event.rideId());
        if (ranking.isEmpty()) {
            log.error("Empty ranking after AI + fallback | rideId={}", event.rideId());
            return;
        }

        assignDriverWithLock(ranking, event);
    }

    private List<RankingEntry> callAiWithFallback(List<DriverFeatureDto> candidates, String rideId) {
        try {
            log.info("Calling AI scoring | candidates={} | rideId={}", candidates.size(), rideId);
            AiScoringResponse response = aiScoringClient.getBestMatch(candidates);
            log.info("AI suggested bestDriver={} | score={} | rideId={}",
                    response.getBestDriverId(), response.getHighestScore(), rideId);
            return response.getRanking() != null ? response.getRanking() : List.of();
        } catch (Exception ex) {
            log.error("AI scoring failed, fallback to nearest drivers | rideId={} | reason={}",
                    rideId, ex.getMessage());
            List<RankingEntry> fallbackRanking = new ArrayList<>();
            for (DriverFeatureDto candidate : candidates) {
                fallbackRanking.add(RankingEntry.builder()
                        .driverId(candidate.getDriverId())
                        .score(0.0)
                        .details("fallback-nearest-driver")
                        .build());
            }
            return fallbackRanking;
        }
    }

    private void assignDriverWithLock(List<RankingEntry> ranking, RideCreatedEvent event) {
        log.info("Assigning with Redis lock | candidates={} | rideId={}", ranking.size(), event.rideId());

        for (RankingEntry entry : ranking) {
            String driverId = normalizeDriverId(entry.getDriverId());
            String lockKey = DRIVER_LOCK_PREFIX + driverId;

            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, DRIVER_LOCK_VALUE, LOCK_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                RideAssignedEvent assignedEvent = RideAssignedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("DRIVER_ASSIGNED")
                        .rideId(event.rideId())
                        .bookingId(event.rideId())
                        .driverId(driverId)
                        .timestamp(Instant.now().toString())
                        .build();

                kafkaTemplate.send(TOPIC_DRIVER_ASSIGNED, event.rideId(), assignedEvent);
                kafkaTemplate.send(TOPIC_RIDE_ASSIGNED_LEGACY, event.rideId(), assignedEvent);

                log.info("Published driver.assigned and legacy ride.assigned | rideId={} | driverId={}",
                        event.rideId(), driverId);
                return;
            }

            log.warn("Driver lock already taken | driverId={} | rideId={}", driverId, event.rideId());
        }

        log.warn("No driver remained after lock loop | rideId={}", event.rideId());
        scheduleRetryIfPossible(event);
    }

    private List<DriverFeatureDto> fetchCandidatesFromRedis(
            Double lat,
            Double lng,
            double radiusKm,
            VehicleType requestedVehicleType,
            List<String> excludedDriverIds) {
        if (lat == null || lng == null) {
            log.warn("Missing pickup coordinates, cannot match | lat={} | lng={}", lat, lng);
            return Collections.emptyList();
        }

        GeoReference<String> reference = GeoReference.fromCoordinate(lng, lat);
        Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(10);

        List<DriverFeatureDto> featureList = new ArrayList<>();

        try {
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    redisTemplate.opsForGeo().search(DRIVER_LOCATION_KEY, reference, radius, args);

            if (results == null || results.getContent().isEmpty()) {
                return Collections.emptyList();
            }

            List<String> statusKeys = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                statusKeys.add(DRIVER_STATUS_PREFIX + normalizeDriverId(result.getContent().getName()));
            }
            List<String> statuses = redisTemplate.opsForValue().multiGet(statusKeys);

            for (int i = 0; i < results.getContent().size(); i++) {
                GeoResult<RedisGeoCommands.GeoLocation<String>> result = results.getContent().get(i);
                String driverId = normalizeDriverId(result.getContent().getName());
                if (isExcluded(driverId, excludedDriverIds)) {
                    log.debug("Driver {} is excluded for this rematch, skipping.", driverId);
                    continue;
                }

                String status = statuses != null && i < statuses.size() ? statuses.get(i) : null;
                if (!DRIVER_STATUS_AVAILABLE.equals(status)) {
                    log.debug("Driver {} status is {}, skipping.", driverId, status);
                    continue;
                }

                VehicleType driverVehicleType = resolveDriverVehicleType(driverId);
                if (driverVehicleType != requestedVehicleType) {
                    log.debug("Driver {} vehicleType is {}, requested {}, skipping.",
                            driverId, driverVehicleType, requestedVehicleType);
                    continue;
                }

                double distanceKm = result.getDistance().getValue();
                int etaMin = Math.max(2, (int) (distanceKm * 3));
                double rating = 4.5 + random.nextDouble() * 0.5;
                double priceMultiplier = 1.0 + random.nextDouble() * 0.5;

                featureList.add(DriverFeatureDto.builder()
                        .driverId(driverId)
                        .distance(distanceKm)
                        .eta(etaMin)
                        .rating(rating)
                        .priceMultiplier(priceMultiplier)
                        .build());
            }

            log.info("Fetched {} driver candidates after AVAILABLE + vehicleType filter | vehicleType={}",
                    featureList.size(), requestedVehicleType);
        } catch (Exception ex) {
            log.error("Redis GEO candidate lookup failed | reason={}", ex.getMessage(), ex);
        }

        return featureList;
    }

    public void processDriverRejected(DriverRejectedEvent event) {
        String rideId = event.aggregateId();
        if (rideId == null || rideId.isBlank()) {
            log.warn("Skip driver.rejected without rideId");
            return;
        }

        RideCreatedEvent cached = restoreMatchingRequest(rideId, event.getDriverId());
        if (cached == null) {
            log.warn("No cached matching request for rejected rideId={}, cannot rematch in matching-service", rideId);
            return;
        }

        processMatching(cached);
    }

    private void scheduleRetryIfPossible(RideCreatedEvent event) {
        int currentAttempt = event.attemptOrDefault();
        if (currentAttempt >= MAX_MATCHING_ATTEMPTS || isBookingCancelled(event.rideId())) {
            log.warn("No retry for rideId={} | attempt={} | cancelled={}",
                    event.rideId(), currentAttempt, isBookingCancelled(event.rideId()));
            return;
        }

        int nextAttempt = currentAttempt + 1;
        double nextRadiusKm = switch (nextAttempt) {
            case 2 -> 5.0;
            case 3 -> 8.0;
            default -> event.searchRadiusKmOrDefault();
        };

        String normalizedVehicleType;
        try {
            normalizedVehicleType = VehicleTypeNormalizer.normalizeVehicleType(event.vehicleType()).name();
        } catch (IllegalArgumentException ex) {
            log.warn("Skip matching retry with invalid vehicleType | rideId={} | rawVehicleType={}",
                    event.rideId(), event.vehicleType());
            return;
        }

        RideCreatedEvent retryEvent = new RideCreatedEvent(
                UUID.randomUUID().toString(),
                event.type(),
                event.rideId(),
                event.customerId(),
                event.customerNote(),
                event.pickup(),
                event.dropoff(),
                normalizedVehicleType,
                event.paymentMethod(),
                event.estimatedFare(),
                event.promoCode(),
                nextAttempt,
                nextRadiusKm,
                event.isRematch(),
                event.excludedDriverIds(),
                Instant.now().toString()
        );

        java.util.concurrent.CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            if (isBookingCancelled(event.rideId())) {
                log.info("Skip scheduled retry for cancelled rideId={}", event.rideId());
                return;
            }
            kafkaTemplate.send(TOPIC_BOOKING_CREATED, retryEvent.rideId(), retryEvent);
            kafkaTemplate.send(TOPIC_RIDE_CREATED_LEGACY, retryEvent.rideId(), retryEvent);
            log.info("Scheduled matching retry published | rideId={} | attempt={} | radiusKm={}",
                    retryEvent.rideId(), retryEvent.matchingAttempt(), retryEvent.searchRadiusKm());
        });
    }

    private VehicleType resolveDriverVehicleType(String driverId) {
        String rawVehicleType = redisTemplate.opsForValue().get(DRIVER_VEHICLE_TYPE_PREFIX + driverId);
        if (rawVehicleType == null || rawVehicleType.isBlank()) {
            Object profileVehicleType = redisTemplate.opsForHash().get(DRIVER_PROFILE_PREFIX + driverId, "vehicleType");
            if (profileVehicleType == null) {
                profileVehicleType = redisTemplate.opsForHash().get(DRIVER_PROFILE_PREFIX + driverId, "vehicle_type");
            }
            rawVehicleType = profileVehicleType == null ? null : profileVehicleType.toString();
        }

        try {
            return VehicleTypeNormalizer.normalizeVehicleType(rawVehicleType);
        } catch (IllegalArgumentException ex) {
            log.warn("Driver {} has missing/invalid vehicleType metadata '{}', skipping.", driverId, rawVehicleType);
            return null;
        }
    }

    private void cacheMatchingRequest(RideCreatedEvent event) {
        if (event.rideId() == null || event.rideId().isBlank()) {
            return;
        }

        Map<String, String> values = new HashMap<>();
        put(values, "type", event.type());
        put(values, "rideId", event.rideId());
        put(values, "customerId", event.customerId());
        put(values, "customerNote", event.customerNote());
        put(values, "pickupLat", event.pickupLat());
        put(values, "pickupLng", event.pickupLng());
        put(values, "dropoffLat", coordinate(event.dropoff(), "lat"));
        put(values, "dropoffLng", coordinate(event.dropoff(), "lng"));
        put(values, "vehicleType", event.vehicleType());
        put(values, "paymentMethod", event.paymentMethod());
        put(values, "estimatedFare", event.estimatedFare());
        put(values, "promoCode", event.promoCode());
        put(values, "matchingAttempt", event.attemptOrDefault());
        put(values, "searchRadiusKm", event.searchRadiusKmOrDefault());
        put(values, "excludedDriverIds", String.join(",", normalizeList(event.excludedDriverIds())));

        String key = MATCHING_REQUEST_PREFIX + event.rideId();
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
    }

    private RideCreatedEvent restoreMatchingRequest(String rideId, String rejectedDriverId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(MATCHING_REQUEST_PREFIX + rideId);
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        List<String> excluded = normalizeList(split(raw.get("excludedDriverIds")));
        String normalizedRejectedDriverId = normalizeDriverId(rejectedDriverId);
        if (normalizedRejectedDriverId != null && !excluded.contains(normalizedRejectedDriverId)) {
            excluded.add(normalizedRejectedDriverId);
        }

        int nextAttempt = parseInt(raw.get("matchingAttempt"), 1) + 1;
        double nextRadiusKm = switch (Math.min(nextAttempt, MAX_MATCHING_ATTEMPTS)) {
            case 2 -> 5.0;
            case 3 -> 8.0;
            default -> parseDouble(raw.get("searchRadiusKm"), 3.0);
        };

        return new RideCreatedEvent(
                UUID.randomUUID().toString(),
                stringValue(raw.get("type"), "RideCreated"),
                rideId,
                stringValue(raw.get("customerId"), null),
                stringValue(raw.get("customerNote"), null),
                coordinateMap(parseDoubleObject(raw.get("pickupLat")), parseDoubleObject(raw.get("pickupLng"))),
                coordinateMap(parseDoubleObject(raw.get("dropoffLat")), parseDoubleObject(raw.get("dropoffLng"))),
                stringValue(raw.get("vehicleType"), null),
                stringValue(raw.get("paymentMethod"), null),
                null,
                stringValue(raw.get("promoCode"), null),
                nextAttempt,
                nextRadiusKm,
                true,
                excluded,
                Instant.now().toString());
    }

    private boolean isExcluded(String driverId, List<String> excludedDriverIds) {
        return normalizeList(excludedDriverIds).contains(normalizeDriverId(driverId));
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String normalizedValue = normalizeDriverId(value);
            if (normalizedValue != null && !normalizedValue.isBlank() && !normalized.contains(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private List<String> split(Object value) {
        String raw = value == null ? "" : value.toString();
        if (raw.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(raw.split(",")));
    }

    private Map<String, Double> coordinateMap(Double lat, Double lng) {
        Map<String, Double> coordinates = new HashMap<>();
        coordinates.put("lat", lat);
        coordinates.put("lng", lng);
        return coordinates;
    }

    private Double coordinate(Map<String, Double> coordinates, String key) {
        return coordinates == null ? null : coordinates.get(key);
    }

    private void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, value.toString());
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int parseInt(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseDouble(Object value, double fallback) {
        Double parsed = parseDoubleObject(value);
        return parsed == null ? fallback : parsed;
    }

    private Double parseDoubleObject(Object value) {
        try {
            return value == null ? null : Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBookingCancelled(String rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BOOKING_CANCELLED_PREFIX + rideId));
    }

    private String normalizeDriverId(String driverId) {
        if (driverId == null) {
            return null;
        }
        return driverId.replace("\"", "").trim();
    }
}
