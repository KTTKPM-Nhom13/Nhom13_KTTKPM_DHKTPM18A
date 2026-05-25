package com.cab.ride.core.service;

import com.cab.ride.core.dto.event.DriverLocationEvent;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.repository.RideRepository;
import com.cab.ride.core.dto.socket.response.DriverLocationUpdatedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared service for driver location updates — used by both REST fallback and Socket.IO.
 *
 * <p>Validates ride ownership and status, updates ride:tracking:{rideId} Redis HASH,
 * publishes Kafka event, and returns a response DTO for Socket.IO broadcast.
 *
 * <p>NOTE: This service no longer writes to the old "driver:locations" Redis GEO key.
 * Ride tracking uses a Redis HASH per ride. Driver availability GEO is managed by
 * driver-service via "driver:available:locations".
 */
@Slf4j
@Service
public class RideLocationService {

    private static final String RIDE_TRACKING_PREFIX = "ride:tracking:";
    private static final String KAFKA_LOCATION_TOPIC = "driver.location.updated";

    /**
     * Allowed ride statuses for GPS streaming.
     * ASSIGNED is excluded — driver has not accepted the ride yet, so GPS should not stream.
     */
    private static final Set<RideStatus> VALID_LOCATION_STATUSES = Set.of(
            RideStatus.ACCEPTED,
            RideStatus.PICKUP,
            RideStatus.IN_PROGRESS
    );

    private final RideRepository rideRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RideLocationService(RideRepository rideRepository,
                                RedisTemplate<String, Object> redisTemplate,
                                KafkaTemplate<String, Object> kafkaTemplate) {
        this.rideRepository = rideRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Validates and processes a driver location update.
     *
     * @param driverId the authenticated driver ID (from JWT)
     * @param rideId   the ride ID to update location for
     * @param lat      latitude
     * @param lng      longitude
     * @param heading  optional heading in degrees
     * @param speed    optional speed in km/h
     * @return response DTO for Socket.IO broadcast
     * @throws LocationValidationException if validation fails
     */
    public DriverLocationUpdatedResponse updateLocation(String driverId, String rideId,
                                                         double lat, double lng,
                                                         Double heading, Double speed) {
        // 1. Validate coordinates
        validateCoordinates(lat, lng);

        // 2. Validate ride exists
        Ride ride = rideRepository.findById(parseUuid(rideId))
                .orElseThrow(() -> new LocationValidationException("NOT_FOUND", "Ride not found: " + rideId));

        // 3. Validate driver is assigned to this ride
        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new LocationValidationException("FORBIDDEN", "Driver is not assigned to this ride");
        }

        // 4. Validate ride status
        if (!VALID_LOCATION_STATUSES.contains(ride.getStatus())) {
            throw new LocationValidationException("INVALID_STATUS",
                    "Cannot update location for ride in status: " + ride.getStatus());
        }

        // 5. Update ride tracking hash (replaces old driver:locations GEO)
        updateRideTrackingHash(rideId, driverId, lat, lng, heading, speed);

        // 6. Build event payload
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        DriverLocationEvent kafkaEvent = DriverLocationEvent.builder()
                .eventId(eventId)
                .eventType("DRIVER_LOCATION_UPDATED")
                .rideId(rideId)
                .bookingId(ride.getBookingId() != null ? ride.getBookingId() : rideId)
                .driverId(driverId)
                .lat(lat)
                .lng(lng)
                .heading(heading)
                .speed(speed)
                .timestamp(timestamp)
                .build();

        // 7. Publish Kafka (async, fire-and-forget)
        // Reserved for future consumers (analytics, ETA, heatmap, fraud detection, notification-service)
        publishKafka(driverId, kafkaEvent);

        // 8. Return response for Socket.IO broadcast
        return DriverLocationUpdatedResponse.builder()
                .eventId(eventId)
                .eventType("DRIVER_LOCATION_UPDATED")
                .rideId(rideId)
                .bookingId(kafkaEvent.getBookingId())
                .driverId(driverId)
                .lat(lat)
                .lng(lng)
                .heading(heading)
                .speed(speed)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Simplified overload — DEPRECATED. Kept for backward compatibility only.
     * Production callers must use {@link #updateLocation(String, String, double, double, Double, Double)}.
     *
     * @deprecated Use the ride-aware overload with rideId instead.
     */
    @Deprecated
    public void updateLocationSimple(String driverId, double lat, double lng) {
        String activeRideId = findActiveRideIdForDriver(driverId);
        if (activeRideId != null) {
            updateRideTrackingHash(activeRideId, driverId, lat, lng, null, null);
        }

        String ts = Instant.now().toString();
        DriverLocationEvent event = DriverLocationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DRIVER_LOCATION_UPDATED")
                .rideId(activeRideId)
                .driverId(driverId)
                .lat(lat)
                .lng(lng)
                .timestamp(ts)
                .build();

        publishKafka(driverId, event);
    }

    /**
     * Update ride:tracking:{rideId} Redis HASH with latest driver position.
     * This replaces the old driver:locations GEO approach.
     * Purpose: last known position, reconnect support, fallback state.
     */
    private void updateRideTrackingHash(String rideId, String driverId,
                                         double lat, double lng,
                                         Double heading, Double speed) {
        try {
            String key = RIDE_TRACKING_PREFIX + rideId;
            Map<String, String> tracking = new HashMap<>();
            tracking.put("lat", String.valueOf(lat));
            tracking.put("lng", String.valueOf(lng));
            tracking.put("driverId", driverId);
            tracking.put("updatedAt", Instant.now().toString());
            if (heading != null) {
                tracking.put("heading", String.valueOf(heading));
            }
            if (speed != null) {
                tracking.put("speed", String.valueOf(speed));
            }
            redisTemplate.opsForHash().putAll(key, tracking);
            redisTemplate.expire(key, 86400, TimeUnit.SECONDS); // 24h safety TTL
            log.debug("[RideLocationService] Ride tracking hash updated: key={} driverId={} lat={} lng={}",
                    key, driverId, lat, lng);
        } catch (Exception ex) {
            log.error("[RideLocationService] Failed to update ride tracking hash: rideId={} error={}",
                    rideId, ex.getMessage(), ex);
        }
    }

    /**
     * Remove ride:tracking:{rideId} when ride ends.
     * Called from RideService on COMPLETED/CANCELLED.
     */
    public void cleanupTrackingHash(String rideId) {
        try {
            String key = RIDE_TRACKING_PREFIX + rideId;
            Boolean deleted = redisTemplate.delete(key);
            log.info("[RideLocationService] Cleaned up ride tracking hash: key={} deleted={}", key, deleted);
        } catch (Exception ex) {
            log.error("[RideLocationService] Failed to cleanup ride tracking hash: rideId={} error={}",
                    rideId, ex.getMessage(), ex);
        }
    }

    private void publishKafka(String driverId, DriverLocationEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KAFKA_LOCATION_TOPIC, driverId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[RideLocationService] Kafka send failed: topic={} driverId={} error={}",
                        KAFKA_LOCATION_TOPIC, driverId, ex.getMessage(), ex);
            } else {
                log.debug("[RideLocationService] Kafka sent: topic={} driverId={} partition={} offset={}",
                        KAFKA_LOCATION_TOPIC, driverId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private String findActiveRideIdForDriver(String driverId) {
        if (driverId == null || driverId.isBlank()) return null;
        return rideRepository
                .findFirstByDriverIdAndStatusNotIn(driverId,
                        List.of(RideStatus.COMPLETED, RideStatus.PAID, RideStatus.CANCELLED))
                .map(ride -> ride.getId().toString())
                .orElse(null);
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90.0 || lat > 90.0) {
            throw new LocationValidationException("BAD_REQUEST", "Invalid latitude: " + lat);
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new LocationValidationException("BAD_REQUEST", "Invalid longitude: " + lng);
        }
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new LocationValidationException("BAD_REQUEST", "Invalid rideId format: " + id);
        }
    }

    /**
     * Exception thrown when location update validation fails.
     */
    public static class LocationValidationException extends RuntimeException {
        private final String errorCode;

        public LocationValidationException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
