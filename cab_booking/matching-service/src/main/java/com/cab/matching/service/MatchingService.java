package com.cab.matching.service;

import com.cab.matching.client.AiScoringResilienceClient;
import com.cab.matching.client.DriverFeatureDto;
import com.cab.matching.client.RankingEntry;
import com.cab.matching.core.dto.event.inbound.DriverRejectedEvent;
import com.cab.matching.core.dto.event.inbound.RideCancelledEvent;
import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.core.dto.event.outbound.MatchingFailedEvent;
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

import java.math.BigDecimal;
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

    private static final String DRIVER_LOCATION_KEY = "driver:available:locations";
    private static final String DRIVER_STATUS_PREFIX = "driver:status:";
    private static final String DRIVER_VEHICLE_TYPE_PREFIX = "driver:vehicleType:";
    private static final String DRIVER_PROFILE_PREFIX = "driver:profile:";
    private static final String DRIVER_LOCK_PREFIX = "driver:lock:";
    private static final String MATCHING_REQUEST_PREFIX = "matching:request:";
    private static final String MATCHING_LOCK_PREFIX = "matching:lock:";
    private static final String MATCHING_ASSIGNED_PREFIX = "matching:assigned:";
    private static final String MATCHING_FAILED_PREFIX = "matching:failed:";
    private static final String MATCHING_DRIVER_PREFIX = "matching:driver:";
    private static final String MATCHING_ATTEMPT_PREFIX = "matching:attempt:";
    private static final String MATCHING_COOLDOWN_PREFIX = "matching:cooldown:";
    private static final String RIDE_REJECTED_DRIVERS_PREFIX = "ride:%s:rejected-drivers";
    private static final String DRIVER_STATUS_AVAILABLE = "AVAILABLE";
    private static final String DRIVER_LOCK_VALUE = "LOCKED";
    private static final String MATCHING_LOCK_VALUE = "LOCKED";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";
    private static final long LOCK_TTL_SECONDS = 60L;
    private static final long MATCHING_LOCK_TTL_SECONDS = 60L;
    private static final long MATCHING_STATE_TTL_HOURS = 2L;
    private static final long RETRY_DELAY_SECONDS = 5L;
    private static final int MAX_MATCHING_ATTEMPTS = 3;
    private static final long COOLDOWN_SECONDS = 30L;
    private static final String TOPIC_RIDE_ASSIGNED = "ride.assigned";
    private static final String TOPIC_RIDE_CANCELLED = "ride.cancelled";
    private static final String TOPIC_MATCHING_RETRY_REQUESTED = "matching.retry.requested";
    private static final String TOPIC_MATCHING_FAILED = "matching.failed";

    private final RedisTemplate<String, String> redisTemplate;
    private final AiScoringResilienceClient aiScoringResilienceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final Random random = new Random();

    public void processMatching(RideCreatedEvent event) {
        if (event == null || event.rideId() == null || event.rideId().isBlank()) {
            log.warn("Skip matching event without rideId");
            return;
        }

        String rideId = event.rideId();
        if (isBookingCancelled(rideId)) {
            log.info("Skip matching cancelled rideId={}", rideId);
            return;
        }
        if (hasAssigned(rideId)) {
            log.info("Skip duplicate matching event because ride is already assigned | rideId={}", rideId);
            return;
        }
        // Cooldown check: if a cooldown key exists, skip this attempt to prevent tight loops.
        // This replaces the old permanent hasFailed() block.
        if (isCoolingDown(rideId)) {
            log.info("Skip matching event during cooldown period | rideId={}", rideId);
            return;
        }

        int eventAttempt = event.attemptOrDefault();
        int latestAttempt = latestAttempt(rideId);
        if (latestAttempt >= eventAttempt) {
            log.info("Skip duplicate/stale matching event | rideId={} | eventAttempt={} | latestAttempt={}",
                    rideId, eventAttempt, latestAttempt);
            return;
        }

        String matchingLockKey = MATCHING_LOCK_PREFIX + rideId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(matchingLockKey, MATCHING_LOCK_VALUE, MATCHING_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Matching already in progress, skip duplicate | rideId={}", rideId);
            return;
        }

        try {
            recordAttempt(rideId, eventAttempt);
            processMatchingAttempt(event);
        } finally {
            redisTemplate.delete(matchingLockKey);
        }
    }

    private void processMatchingAttempt(RideCreatedEvent event) {
        cacheMatchingRequest(event);

        VehicleType requestedVehicleType;
        try {
            requestedVehicleType = VehicleTypeNormalizer.normalizeVehicleType(event.vehicleType());
        } catch (IllegalArgumentException ex) {
            log.warn("Skip matching event with invalid vehicleType | rideId={} | rawVehicleType={} | reason={}",
                    event.rideId(), event.vehicleType(), ex.getMessage());
            return;
        }

        log.info("Start matching | rideId={} | vehicleType={} | attempt={}", event.rideId(), requestedVehicleType, event.attemptOrDefault());

        List<String> excludedDriverIds = withRejectedDrivers(event.rideId(), event.excludedDriverIds());
        List<DriverFeatureDto> candidates = fetchCandidatesFromRedis(
                event.pickupLat(),
                event.pickupLng(),
                event.searchRadiusKmOrDefault(),
                requestedVehicleType,
                excludedDriverIds);

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
        return aiScoringResilienceClient.rankCandidates(candidates, rideId);
    }

    private void assignDriverWithLock(List<RankingEntry> ranking, RideCreatedEvent event) {
        log.info("Assigning with Redis lock | candidates={} | rideId={}", ranking.size(), event.rideId());

        for (RankingEntry entry : ranking) {
            String driverId = normalizeDriverId(entry.getDriverId());
            String lockKey = DRIVER_LOCK_PREFIX + driverId;

            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, event.rideId(), LOCK_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                if (isBookingCancelled(event.rideId()) || hasAssigned(event.rideId())) {
                    log.info("Ride already cancelled/assigned after driver lock, releasing | rideId={} | driverId={}",
                            event.rideId(), driverId);
                    releaseDriverLockIfOwned(driverId, event.rideId());
                    return;
                }
                RideAssignedEvent assignedEvent = RideAssignedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("DRIVER_ASSIGNED")
                        .rideId(event.rideId())
                        .bookingId(event.rideId())
                        .driverId(driverId)
                        .customerId(event.customerId())
                        .pickupAddress(event.pickupAddress())
                        .dropoffAddress(event.dropoffAddress())
                        .pickup(event.pickup())
                        .dropoff(event.dropoff())
                        .vehicleType(event.vehicleType())
                        .paymentMethod(event.paymentMethod())
                        .estimatedFare(event.estimatedFare())
                        .timestamp(Instant.now().toString())
                        .build();

                kafkaTemplate.send(TOPIC_RIDE_ASSIGNED, event.rideId(), assignedEvent);
                markAssigned(event.rideId(), driverId);

                log.info("Published ride.assigned | rideId={} | driverId={}",
                        event.rideId(), driverId);
                return;
            }

            log.warn("Driver lock already taken | driverId={} | rideId={} | owner={}",
                    driverId, event.rideId(), redisTemplate.opsForValue().get(lockKey));
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
            log.warn("Skip ride.rejected without rideId");
            return;
        }

        String rejectedDriverId = normalizeDriverId(event.getDriverId());
        if (rejectedDriverId != null && !rejectedDriverId.isBlank()) {
            releaseDriverLockIfOwned(rejectedDriverId, rideId);
            redisTemplate.opsForSet().add(rejectedDriversKey(rideId), rejectedDriverId);
            redisTemplate.expire(rejectedDriversKey(rideId), 15, TimeUnit.MINUTES);
            log.info("Released driver lock after ride.rejected | rideId={} | driverId={}", rideId, rejectedDriverId);
        }
        redisTemplate.delete(MATCHING_ASSIGNED_PREFIX + rideId);
        redisTemplate.delete(MATCHING_DRIVER_PREFIX + rideId);

        RideCreatedEvent cached = restoreMatchingRequest(rideId, event.getDriverId());
        if (cached == null) {
            log.warn("No cached matching request for rejected rideId={}, cannot rematch in matching-service", rideId);
            return;
        }

        processMatching(cached);
    }

    /**
     * Schedule a retry if the booking is still active.
     *
     * <p>When max attempts within a cycle are reached:
     * <ol>
     *   <li>Publish {@code matching.failed} for observability</li>
     *   <li>Set a cooldown key ({@code matching:cooldown:{rideId}}) with 30s TTL</li>
     *   <li>Schedule a new cycle retry after the cooldown expires</li>
     * </ol>
     * <p>This creates a cycle-based retry pattern:
     * cycle 1: attempts 1→2→3 → cooldown 30s → cycle 2: attempts 1→2→3 → cooldown 30s → ...
     * <p>The loop terminates only when:
     * <ul>
     *   <li>BookingTimeoutScheduler publishes ride.cancelled</li>
     *   <li>A driver is successfully assigned (ride.assigned)</li>
     * </ul>
     */
    private void scheduleRetryIfPossible(RideCreatedEvent event) {
        int currentAttempt = event.attemptOrDefault();
        if (currentAttempt >= MAX_MATCHING_ATTEMPTS) {
            // Max attempts in this cycle reached — enter cooldown before next cycle
            log.warn("No driver available after {} attempts in current cycle; entering cooldown | rideId={}",
                    currentAttempt, event.rideId());

            // Publish matching.failed for observability (does NOT permanently block)
            publishMatchingFailed(event, currentAttempt, "NO_DRIVER_AVAILABLE");

            // Set cooldown key to prevent tight loops; schedule next cycle after cooldown
            setCooldownAndScheduleCycleRetry(event);
            return;
        }

        // Still within a cycle — schedule next attempt with short delay
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
                event.pickupAddress(),
                event.dropoffAddress(),
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

        java.util.concurrent.CompletableFuture.delayedExecutor(RETRY_DELAY_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (isBookingCancelled(event.rideId()) || hasAssigned(event.rideId())) {
                log.info("Skip scheduled retry for cancelled/assigned rideId={}", event.rideId());
                return;
            }
            kafkaTemplate.send(TOPIC_MATCHING_RETRY_REQUESTED, retryEvent.rideId(), retryEvent);
            log.info("Scheduled matching retry requested | topic={} | rideId={} | attempt={} | radiusKm={} | delaySeconds={}",
                    TOPIC_MATCHING_RETRY_REQUESTED, retryEvent.rideId(), retryEvent.matchingAttempt(),
                    retryEvent.searchRadiusKm(), RETRY_DELAY_SECONDS);
        });
    }

    /**
     * Set a cooldown key and schedule a new matching cycle after the cooldown expires.
     * The cooldown prevents tight retry loops when no drivers are available.
     *
     * <p>The new cycle resets attempt to 1 with the original search radius,
     * effectively starting a fresh matching cycle.</p>
     */
    private void setCooldownAndScheduleCycleRetry(RideCreatedEvent event) {
        String cooldownKey = MATCHING_COOLDOWN_PREFIX + event.rideId();
        redisTemplate.opsForValue().set(cooldownKey, "COOLING", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        log.info("Set matching cooldown | rideId={} | cooldownSeconds={}", event.rideId(), COOLDOWN_SECONDS);

        java.util.concurrent.CompletableFuture.delayedExecutor(COOLDOWN_SECONDS, TimeUnit.SECONDS).execute(() -> {
            // Clean up cooldown key (it will auto-expire, but delete early for clarity)
            redisTemplate.delete(cooldownKey);

            // Pre-checks before starting a new cycle
            if (isBookingCancelled(event.rideId())) {
                log.info("Skip cooldown retry for cancelled rideId={}", event.rideId());
                return;
            }
            if (hasAssigned(event.rideId())) {
                log.info("Skip cooldown retry — ride already assigned | rideId={}", event.rideId());
                return;
            }

            // Build a fresh cycle retry event with attempt=1 and original radius
            String normalizedVehicleType;
            try {
                normalizedVehicleType = VehicleTypeNormalizer.normalizeVehicleType(event.vehicleType()).name();
            } catch (IllegalArgumentException ex) {
                log.warn("Skip cooldown retry with invalid vehicleType | rideId={} | rawVehicleType={}",
                        event.rideId(), event.vehicleType());
                return;
            }

            RideCreatedEvent cycleRetryEvent = new RideCreatedEvent(
                    UUID.randomUUID().toString(),
                    event.type(),
                    event.rideId(),
                    event.customerId(),
                    event.customerNote(),
                    event.pickupAddress(),
                    event.dropoffAddress(),
                    event.pickup(),
                    event.dropoff(),
                    normalizedVehicleType,
                    event.paymentMethod(),
                    event.estimatedFare(),
                    event.promoCode(),
                    1,  // reset attempt to 1 for new cycle
                    event.searchRadiusKmOrDefault(),
                    true,
                    event.excludedDriverIds(),
                    Instant.now().toString()
            );

            kafkaTemplate.send(TOPIC_MATCHING_RETRY_REQUESTED, cycleRetryEvent.rideId(), cycleRetryEvent);
            log.info("Scheduled new matching cycle after cooldown | topic={} | rideId={} | cooldownSeconds={}",
                    TOPIC_MATCHING_RETRY_REQUESTED, cycleRetryEvent.rideId(), COOLDOWN_SECONDS);
        });
    }

    public void processRideCancelled(RideCancelledEvent event) {
        String rideId = event.getRideId() != null ? event.getRideId() : event.getBookingId();
        if (rideId == null || rideId.isBlank()) {
            log.warn("Skip ride.cancelled without rideId/bookingId");
            return;
        }

        redisTemplate.opsForValue().set(BOOKING_CANCELLED_PREFIX + rideId, "true", MATCHING_STATE_TTL_HOURS, TimeUnit.HOURS);
        releaseAssignedDriverLock(rideId, event.getDriverId());
        redisTemplate.delete(MATCHING_LOCK_PREFIX + rideId);
        redisTemplate.delete(MATCHING_REQUEST_PREFIX + rideId);
        redisTemplate.delete(MATCHING_ASSIGNED_PREFIX + rideId);
        redisTemplate.delete(MATCHING_DRIVER_PREFIX + rideId);
        redisTemplate.delete(MATCHING_ATTEMPT_PREFIX + rideId);
        redisTemplate.delete(MATCHING_COOLDOWN_PREFIX + rideId);
        redisTemplate.delete(rejectedDriversKey(rideId));
        log.info("Processed ride.cancelled cleanup in matching-service | rideId={}", rideId);
    }

    private void markAssigned(String rideId, String driverId) {
        redisTemplate.opsForValue().set(MATCHING_ASSIGNED_PREFIX + rideId, "true", MATCHING_STATE_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(MATCHING_DRIVER_PREFIX + rideId, driverId, MATCHING_STATE_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * @deprecated No longer used for permanent failure blocking.
     * Kept for reference. The new periodic rematch flow uses cooldown keys instead.
     */
    @Deprecated
    private void markFailed(String rideId, String reason) {
        redisTemplate.opsForValue().set(MATCHING_FAILED_PREFIX + rideId, reason, MATCHING_STATE_TTL_HOURS, TimeUnit.HOURS);
        releaseAssignedDriverLock(rideId, null);
    }

    private void recordAttempt(String rideId, int attempt) {
        redisTemplate.opsForValue().set(MATCHING_ATTEMPT_PREFIX + rideId, Integer.toString(attempt),
                MATCHING_STATE_TTL_HOURS, TimeUnit.HOURS);
    }

    private void publishMatchingFailed(RideCreatedEvent event, int attempt, String reason) {
        MatchingFailedEvent failedEvent = MatchingFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MATCHING_FAILED")
                .rideId(event.rideId())
                .bookingId(event.rideId())
                .customerId(event.customerId())
                .attempt(attempt)
                .reason(reason)
                .timestamp(Instant.now().toString())
                .build();
        kafkaTemplate.send(TOPIC_MATCHING_FAILED, event.rideId(), failedEvent);
        log.warn("Published matching.failed | rideId={} | attempt={} | reason={}", event.rideId(), attempt, reason);
    }

    /**
     * Publish ride.cancelled using typed DTO.
     * Currently not called — BookingTimeoutScheduler handles cancellation via booking-service.
     * Kept for potential future use (e.g., explicit terminal failure policy).
     */
    private void publishRideCancelled(RideCreatedEvent event, int attempt, String reason) {
        com.cab.matching.core.dto.event.outbound.RideCancelledEvent cancelledEvent =
                com.cab.matching.core.dto.event.outbound.RideCancelledEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("RIDE_CANCELLED")
                        .rideId(event.rideId())
                        .bookingId(event.rideId())
                        .customerId(event.customerId())
                        .reason(reason + " (attempts: " + attempt + ")")
                        .timestamp(Instant.now().toString())
                        .build();

        kafkaTemplate.send(TOPIC_RIDE_CANCELLED, event.rideId(), cancelledEvent);
        log.info("Published ride.cancelled from matching-service | rideId={} | reason={}",
                event.rideId(), reason);
    }

    private void releaseAssignedDriverLock(String rideId, String driverIdFromEvent) {
        String driverId = normalizeDriverId(driverIdFromEvent);
        if (driverId == null || driverId.isBlank()) {
            driverId = redisTemplate.opsForValue().get(MATCHING_DRIVER_PREFIX + rideId);
        }
        if (driverId != null && !driverId.isBlank()) {
            releaseDriverLockIfOwned(driverId, rideId);
            log.info("Released driver lock | rideId={} | driverId={}", rideId, driverId);
        }
    }

    private void releaseDriverLockIfOwned(String driverId, String rideId) {
        String normalizedDriverId = normalizeDriverId(driverId);
        if (normalizedDriverId == null || normalizedDriverId.isBlank()) {
            return;
        }
        String lockKey = DRIVER_LOCK_PREFIX + normalizedDriverId;
        String ownerRideId = redisTemplate.opsForValue().get(lockKey);
        if (ownerRideId == null) {
            return;
        }
        if (ownerRideId.equals(rideId) || DRIVER_LOCK_VALUE.equals(ownerRideId)) {
            redisTemplate.delete(lockKey);
            return;
        }
        log.warn("Skip releasing driver lock owned by another ride | driverId={} | ownerRideId={} | requestedRideId={}",
                normalizedDriverId, ownerRideId, rideId);
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
        put(values, "pickupAddress", event.pickupAddress());
        put(values, "dropoffAddress", event.dropoffAddress());
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
                stringValue(raw.get("pickupAddress"), null),
                stringValue(raw.get("dropoffAddress"), null),
                coordinateMap(parseDoubleObject(raw.get("pickupLat")), parseDoubleObject(raw.get("pickupLng"))),
                coordinateMap(parseDoubleObject(raw.get("dropoffLat")), parseDoubleObject(raw.get("dropoffLng"))),
                stringValue(raw.get("vehicleType"), null),
                stringValue(raw.get("paymentMethod"), null),
                parseBigDecimal(raw.get("estimatedFare")),
                stringValue(raw.get("promoCode"), null),
                nextAttempt,
                nextRadiusKm,
                true,
                excluded,
                Instant.now().toString());
    }

    private boolean isExcluded(String driverId, List<String> excludedDriverIds) {
        String normalizedDriverId = normalizeDriverId(driverId);
        return normalizeList(excludedDriverIds).contains(normalizedDriverId);
    }

    private List<String> withRejectedDrivers(String rideId, List<String> excludedDriverIds) {
        List<String> excluded = normalizeList(excludedDriverIds);
        try {
            java.util.Set<String> rejected = redisTemplate.opsForSet().members(rejectedDriversKey(rideId));
            if (rejected != null) {
                for (String driverId : rejected) {
                    String normalizedDriverId = normalizeDriverId(driverId);
                    if (normalizedDriverId != null && !normalizedDriverId.isBlank()
                            && !excluded.contains(normalizedDriverId)) {
                        excluded.add(normalizedDriverId);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not load rejected driver set | rideId={} | reason={}", rideId, ex.getMessage());
        }
        return excluded;
    }

    private String rejectedDriversKey(String rideId) {
        return String.format(RIDE_REJECTED_DRIVERS_PREFIX, rideId);
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

    private BigDecimal parseBigDecimal(Object value) {
        try {
            return value == null ? null : new BigDecimal(value.toString());
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
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

    private boolean hasAssigned(String rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(MATCHING_ASSIGNED_PREFIX + rideId));
    }

    /**
     * Check if a ride is in cooldown period (between matching cycles).
     * Returns true if the cooldown key exists with a short TTL.
     * This prevents tight retry loops while allowing periodic retries.
     */
    private boolean isCoolingDown(String rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(MATCHING_COOLDOWN_PREFIX + rideId));
    }

    /**
     * @deprecated No longer used for permanent blocking. Replaced by {@link #isCoolingDown(String)}.
     */
    @Deprecated
    private boolean hasFailed(String rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(MATCHING_FAILED_PREFIX + rideId));
    }

    private int latestAttempt(String rideId) {
        return parseInt(redisTemplate.opsForValue().get(MATCHING_ATTEMPT_PREFIX + rideId), 0);
    }

    private String normalizeDriverId(String driverId) {
        if (driverId == null) {
            return null;
        }
        return driverId.replace("\"", "").trim();
    }
}
