package com.cab.ride.core.service;

import com.cab.ride.core.dto.event.DriverLocationEvent;
import com.cab.ride.core.dto.event.inbound.DriverAcceptedEvent;
import com.cab.ride.core.dto.event.inbound.RideCancelledEvent;
import com.cab.ride.core.dto.event.inbound.RideCreatedEvent;
import com.cab.ride.core.dto.event.outbound.RideArrivedEvent;
import com.cab.ride.core.dto.event.outbound.RideCompletedEvent;
import com.cab.ride.core.dto.event.outbound.RideStartedEvent;
import com.cab.ride.core.dto.request.CompleteRideRequest;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private static final String REDIS_GEO_KEY = "driver:locations";
    private static final String KAFKA_LOCATION_TOPIC = "driver.location.updated";
    private static final String TOPIC_RIDE_ARRIVED = "ride.arrived";
    private static final String TOPIC_RIDE_STARTED = "ride.started";
    private static final String TOPIC_RIDE_COMPLETED = "ride.completed";
    private static final String TOPIC_RIDE_FINISHED = "ride.finished";

    private final RideRepository rideRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Ride createRideFromBooking(RideCreatedEvent event) {
        String rideId = event.aggregateId();
        UUID uuid = parseUuid(rideId);
        return rideRepository.findById(uuid)
                .map(existing -> {
                    log.info("[RideService] Duplicate ride.created ignored: rideId={} status={}", rideId, existing.getStatus());
                    return existing;
                })
                .orElseGet(() -> {
                    Ride ride = Ride.builder()
                            .id(uuid)
                            .bookingId(event.getBookingId() != null && !event.getBookingId().isBlank()
                                    ? event.getBookingId()
                                    : event.aggregateId())
                            .customerId(event.getCustomerId())
                            .pickupAddress(event.getPickupAddress())
                            .dropoffAddress(event.getDropoffAddress())
                            .pickupLat(event.pickupLat())
                            .pickupLng(event.pickupLng())
                            .dropoffLat(event.dropoffLat())
                            .dropoffLng(event.dropoffLng())
                            .status(RideStatus.CREATED)
                            .build();
                    Ride saved = rideRepository.save(ride);
                    log.info("[RideService] Ride created from ride.created: rideId={} customerId={}",
                            rideId, event.getCustomerId());
                    return saved;
                });
    }

    @Transactional
    public Ride updateRideStatus(String rideId, RideStatus newStatus) {
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> rideNotFound(rideId));

        if (ride.getStatus() == newStatus) {
            return ride;
        }

        RideStatus oldStatus = ride.getStatus();
        ride.setStatus(newStatus);
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] Status transition: rideId={} | {} -> {}", rideId, oldStatus, newStatus);
        return saved;
    }

    @Transactional
    public Ride assignDriverToRide(String rideId, String driverId, RideStatus newStatus) {
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> rideNotFound(rideId));

        RideStatus oldStatus = ride.getStatus();
        if (oldStatus != RideStatus.MATCHING && oldStatus != RideStatus.CREATED && oldStatus != RideStatus.ASSIGNED) {
            log.warn("[RideService] Invalid assignment transition: rideId={} | current={} | attempted={}",
                    rideId, oldStatus, newStatus);
            return ride;
        }

        if (oldStatus == RideStatus.ASSIGNED && driverId.equals(ride.getDriverId())) {
            log.info("[RideService] Duplicate assignment ignored: rideId={} | driverId={}", rideId, driverId);
            return ride;
        }

        ride.setDriverId(driverId);
        ride.setStatus(newStatus);
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] Driver assigned: rideId={} | driverId={} | {} -> {}",
                rideId, driverId, oldStatus, newStatus);
        return saved;
    }

    @Transactional
    public Ride markDriverAccepted(DriverAcceptedEvent event) {
        String rideId = event.aggregateId();
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid).orElse(null);

        if (ride == null) {
            Ride saved = createAcceptedRide(uuid, event);
            log.info("[RideService] Driver accepted ride from ride.accepted: rideId={} | driverId={}",
                    rideId, event.getDriverId());
            return saved;
        }

        if (isAtLeastAccepted(ride.getStatus())) {
            mergeAcceptedMetadata(ride, event);
            rideRepository.save(ride);
            log.info("[RideService] Duplicate/stale ride.accepted ignored: rideId={} status={}", rideId, ride.getStatus());
            return ride;
        }
        if (!canAcceptFrom(ride.getStatus())) {
            log.warn("[RideService] Invalid ride.accepted transition: rideId={} | current={}",
                    rideId, ride.getStatus());
            return ride;
        }
        if (driverConflicts(ride, event.getDriverId())) {
            log.warn("[RideService] Driver mismatch on accept: rideId={} | assigned={} | event={}",
                    rideId, ride.getDriverId(), event.getDriverId());
            return ride;
        }

        mergeAcceptedMetadata(ride, event);
        ride.setStatus(RideStatus.ACCEPTED);
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] Driver accepted ride: rideId={} | driverId={}", rideId, event.getDriverId());
        return saved;
    }

    @Transactional
    public void markRideCancelled(RideCancelledEvent event) {
        String rideId = event.aggregateId();
        if (rideId == null || rideId.isBlank()) {
            log.warn("[RideService] Skip ride.cancelled without rideId/bookingId");
            return;
        }

        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid).orElse(null);
        if (ride == null) {
            log.info("[RideService] ride.cancelled ignored because ride does not exist | rideId={}", rideId);
            return;
        }
        if (ride.getStatus() == RideStatus.CANCELLED
                || ride.getStatus() == RideStatus.COMPLETED
                || ride.getStatus() == RideStatus.PAID) {
            log.info("[RideService] Duplicate/stale ride.cancelled ignored | rideId={} | status={}",
                    rideId, ride.getStatus());
            return;
        }

        RideStatus oldStatus = ride.getStatus();
        ride.setStatus(RideStatus.CANCELLED);
        rideRepository.save(ride);
        log.info("[RideService] ride.cancelled handled | rideId={} | {} -> CANCELLED | reason={}",
                rideId, oldStatus, event.getReason());
    }

    @Transactional
    public Ride transitionRideLifecycle(String rideId, String driverId, RideStatus nextStatus, String sourceTopic) {
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> rideNotFound(rideId));

        if (ride.getStatus() == nextStatus) {
            log.info("[RideService] Duplicate {} ignored: rideId={} already {}", sourceTopic, rideId, nextStatus);
            return ride;
        }
        if (isDuplicateLifecycleCall(ride.getStatus(), nextStatus)) {
            log.info("[RideService] Duplicate/stale {} ignored: rideId={} | current={} | requested={}",
                    sourceTopic, rideId, ride.getStatus(), nextStatus);
            return ride;
        }
        if (!isValidLifecycleTransition(ride.getStatus(), nextStatus)) {
            log.warn("[RideService] Invalid {} transition: rideId={} | current={} | next={}",
                    sourceTopic, rideId, ride.getStatus(), nextStatus);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid ride transition: current=" + ride.getStatus() + ", next=" + nextStatus);
        }
        if (driverId != null && !driverId.isBlank()) {
            if (ride.getDriverId() != null && !ride.getDriverId().equals(driverId)) {
                log.warn("[RideService] Driver mismatch: rideId={} | assigned={} | event={}",
                        rideId, ride.getDriverId(), driverId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver is not assigned to this ride");
            }
            ride.setDriverId(driverId);
        }

        RideStatus oldStatus = ride.getStatus();
        ride.setStatus(nextStatus);
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] {} handled: rideId={} | {} -> {}", sourceTopic, rideId, oldStatus, nextStatus);
        return saved;
    }

    @Transactional
    public Ride arriveAtPickup(String rideId, String driverId) {
        RideStatus previousStatus = currentStatus(rideId);
        Ride saved = transitionRideLifecycle(rideId, driverId, RideStatus.PICKUP, "POST /api/rides/{rideId}/arrive");
        if (previousStatus != RideStatus.PICKUP && saved.getStatus() == RideStatus.PICKUP) {
            RideArrivedEvent event = RideArrivedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RIDE_ARRIVED")
                    .rideId(rideId)
                    .bookingId(bookingId(saved, rideId))
                    .driverId(driverId)
                    .customerId(saved.getCustomerId())
                    .timestamp(Instant.now().toString())
                    .build();
            kafkaTemplate.send(TOPIC_RIDE_ARRIVED, rideId, event);
            log.info("[RideService] ride.arrived published | rideId={} | driverId={}", rideId, driverId);
        }
        return saved;
    }

    @Transactional
    public Ride startRide(String rideId, String driverId) {
        RideStatus previousStatus = currentStatus(rideId);
        Ride saved = transitionRideLifecycle(rideId, driverId, RideStatus.IN_PROGRESS, "POST /api/rides/{rideId}/start");
        if (previousStatus != RideStatus.IN_PROGRESS && saved.getStatus() == RideStatus.IN_PROGRESS) {
            RideStartedEvent event = RideStartedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RIDE_STARTED")
                    .rideId(rideId)
                    .bookingId(bookingId(saved, rideId))
                    .driverId(driverId)
                    .customerId(saved.getCustomerId())
                    .timestamp(Instant.now().toString())
                    .build();
            kafkaTemplate.send(TOPIC_RIDE_STARTED, rideId, event);
            log.info("[RideService] ride.started published | rideId={} | driverId={}", rideId, driverId);
        }
        return saved;
    }

    @Transactional
    public Ride completeRide(String rideId, String driverId, CompleteRideRequest request) {
        RideStatus previousStatus = currentStatus(rideId);
        Ride saved = transitionRideLifecycle(rideId, driverId, RideStatus.COMPLETED, "POST /api/rides/{rideId}/complete");
        if (previousStatus != RideStatus.COMPLETED && saved.getStatus() == RideStatus.COMPLETED) {
            BigDecimal finalFare = request == null || request.getFinalFare() == null
                    ? BigDecimal.ZERO
                    : request.getFinalFare();
            String paymentMethod = request == null || request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()
                    ? "CASH"
                    : request.getPaymentMethod();

            saved.setFinalFare(finalFare);
            saved.setPaymentMethod(paymentMethod);
            saved = rideRepository.save(saved);

            RideCompletedEvent event = RideCompletedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RIDE_COMPLETED")
                    .type("RIDE_COMPLETED")
                    .rideId(rideId)
                    .bookingId(bookingId(saved, rideId))
                    .driverId(driverId)
                    .customerId(saved.getCustomerId())
                    .finalFare(finalFare)
                    .paymentMethod(paymentMethod)
                    .timestamp(Instant.now().toString())
                    .build();
            kafkaTemplate.send(TOPIC_RIDE_COMPLETED, rideId, event);
            kafkaTemplate.send(TOPIC_RIDE_FINISHED, rideId, event);
            log.info("[RideService] ride.completed published | rideId={} | driverId={}", rideId, driverId);
            log.info("[RideService] ride.finished published for compatibility (deprecated) | rideId={} | driverId={}",
                    rideId, driverId);
        }
        return saved;
    }

    public void updateDriverLocation(String driverId, double lat, double lng) {
        try {
            Long added = redisTemplate.opsForGeo().add(REDIS_GEO_KEY, new Point(lng, lat), driverId);
            log.debug("[RideService] Redis GEO updated: driverId={} | lat={} | lng={} | added={}",
                    driverId, lat, lng, added);
        } catch (Exception ex) {
            log.error("[RideService] FAILED to write Redis GEO: driverId={} | error={}",
                    driverId, ex.getMessage(), ex);
        }

        DriverLocationEvent event = DriverLocationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DRIVER_LOCATION_UPDATED")
                .rideId(activeRideIdForDriver(driverId))
                .driverId(driverId)
                .lat(lat)
                .lng(lng)
                .timestamp(System.currentTimeMillis())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KAFKA_LOCATION_TOPIC, driverId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[RideService] FAILED to send Kafka event: topic={} | driverId={} | error={}",
                        KAFKA_LOCATION_TOPIC, driverId, ex.getMessage(), ex);
            } else {
                log.debug("[RideService] Kafka event sent: topic={} | driverId={} | partition={} | offset={}",
                        KAFKA_LOCATION_TOPIC, driverId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private UUID parseUuid(String rideId) {
        try {
            return UUID.fromString(rideId);
        } catch (IllegalArgumentException ex) {
            log.error("[RideService] Invalid UUID format: rideId={}", rideId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rideId format: " + rideId, ex);
        }
    }

    private boolean isValidLifecycleTransition(RideStatus current, RideStatus next) {
        return switch (next) {
            case PICKUP -> current == RideStatus.ACCEPTED;
            case IN_PROGRESS -> current == RideStatus.PICKUP;
            case COMPLETED -> current == RideStatus.IN_PROGRESS;
            default -> false;
        };
    }

    private boolean isDuplicateLifecycleCall(RideStatus current, RideStatus next) {
        return switch (next) {
            case PICKUP -> current == RideStatus.IN_PROGRESS || current == RideStatus.COMPLETED || current == RideStatus.PAID;
            case IN_PROGRESS -> current == RideStatus.COMPLETED || current == RideStatus.PAID;
            case COMPLETED -> current == RideStatus.PAID;
            default -> false;
        };
    }

    private RideStatus currentStatus(String rideId) {
        return rideRepository.findById(parseUuid(rideId))
                .map(Ride::getStatus)
                .orElseThrow(() -> rideNotFound(rideId));
    }

    private boolean driverConflicts(Ride ride, String driverId) {
        return ride.getDriverId() != null
                && !ride.getDriverId().isBlank()
                && driverId != null
                && !driverId.isBlank()
                && !ride.getDriverId().equals(driverId);
    }

    private Ride createAcceptedRide(UUID uuid, DriverAcceptedEvent event) {
        Ride ride = Ride.builder()
                .id(uuid)
                .bookingId(event.getBookingId() != null && !event.getBookingId().isBlank()
                        ? event.getBookingId()
                        : event.getRideId())
                .driverId(event.getDriverId())
                .customerId(event.getCustomerId())
                .pickupAddress(event.getPickupAddress())
                .dropoffAddress(event.getDropoffAddress())
                .pickupLat(coordinate(event.getPickup(), "lat", event.getPickupLat()))
                .pickupLng(coordinate(event.getPickup(), "lng", event.getPickupLng()))
                .dropoffLat(coordinate(event.getDropoff(), "lat", event.getDropoffLat()))
                .dropoffLng(coordinate(event.getDropoff(), "lng", event.getDropoffLng()))
                .finalFare(event.getFinalFare())
                .paymentMethod(event.getPaymentMethod())
                .status(RideStatus.ACCEPTED)
                .build();
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] Created active ride from ride.accepted | rideId={} | driverId={}",
                event.aggregateId(), event.getDriverId());
        return saved;
    }

    private void mergeAcceptedMetadata(Ride ride, DriverAcceptedEvent event) {
        if (ride.getBookingId() == null || ride.getBookingId().isBlank()) {
            ride.setBookingId(event.getBookingId() != null && !event.getBookingId().isBlank()
                    ? event.getBookingId()
                    : event.aggregateId());
        }
        if (ride.getDriverId() == null || ride.getDriverId().isBlank()) {
            ride.setDriverId(event.getDriverId());
        }
        if (ride.getCustomerId() == null || ride.getCustomerId().isBlank()) {
            ride.setCustomerId(event.getCustomerId());
        }
        if (ride.getPickupAddress() == null || ride.getPickupAddress().isBlank()) {
            ride.setPickupAddress(event.getPickupAddress());
        }
        if (ride.getDropoffAddress() == null || ride.getDropoffAddress().isBlank()) {
            ride.setDropoffAddress(event.getDropoffAddress());
        }
        if (ride.getPickupLat() == null) {
            ride.setPickupLat(coordinate(event.getPickup(), "lat", event.getPickupLat()));
        }
        if (ride.getPickupLng() == null) {
            ride.setPickupLng(coordinate(event.getPickup(), "lng", event.getPickupLng()));
        }
        if (ride.getDropoffLat() == null) {
            ride.setDropoffLat(coordinate(event.getDropoff(), "lat", event.getDropoffLat()));
        }
        if (ride.getDropoffLng() == null) {
            ride.setDropoffLng(coordinate(event.getDropoff(), "lng", event.getDropoffLng()));
        }
        if (ride.getFinalFare() == null) {
            ride.setFinalFare(event.getFinalFare());
        }
        if (ride.getPaymentMethod() == null || ride.getPaymentMethod().isBlank()) {
            ride.setPaymentMethod(event.getPaymentMethod());
        }
    }

    private boolean isAtLeastAccepted(RideStatus status) {
        return status == RideStatus.ACCEPTED
                || status == RideStatus.PICKUP
                || status == RideStatus.IN_PROGRESS
                || status == RideStatus.COMPLETED
                || status == RideStatus.PAID;
    }

    private boolean canAcceptFrom(RideStatus status) {
        return status == RideStatus.CREATED
                || status == RideStatus.MATCHING
                || status == RideStatus.ASSIGNED;
    }

    private Double coordinate(java.util.Map<String, Double> coordinates, String key, Double fallback) {
        return coordinates == null || coordinates.get(key) == null ? fallback : coordinates.get(key);
    }

    private String activeRideIdForDriver(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return null;
        }
        return rideRepository
                .findFirstByDriverIdAndStatusNotIn(driverId, List.of(RideStatus.COMPLETED, RideStatus.PAID, RideStatus.CANCELLED))
                .map(ride -> ride.getId().toString())
                .orElse(null);
    }

    private String bookingId(Ride ride, String fallbackRideId) {
        return ride.getBookingId() == null || ride.getBookingId().isBlank()
                ? fallbackRideId
                : ride.getBookingId();
    }

    private ResponseStatusException rideNotFound(String rideId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found: " + rideId);
    }

}
