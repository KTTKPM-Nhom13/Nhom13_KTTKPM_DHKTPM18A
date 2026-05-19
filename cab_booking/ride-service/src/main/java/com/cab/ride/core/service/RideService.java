package com.cab.ride.core.service;

import com.cab.ride.core.dto.event.DriverLocationEvent;
import com.cab.ride.core.dto.event.inbound.DriverAcceptedEvent;
import com.cab.ride.core.dto.event.inbound.RideArrivedEvent;
import com.cab.ride.core.dto.event.inbound.RideCompletedEvent;
import com.cab.ride.core.dto.event.inbound.RideCreatedEvent;
import com.cab.ride.core.dto.event.inbound.RideStartedEvent;
import com.cab.ride.core.dto.event.outbound.RideFinishedEvent;
import com.cab.ride.core.dto.request.CompleteRideRequest;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
    private static final String TOPIC_RIDE_FINISHED_LEGACY = "ride.finished";

    private final RideRepository rideRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Ride createRideFromBooking(RideCreatedEvent event) {
        String rideId = event.aggregateId();
        UUID uuid = parseUuid(rideId);
        return rideRepository.findById(uuid)
                .map(existing -> {
                    log.info("[RideService] Duplicate booking.created ignored: rideId={} status={}", rideId, existing.getStatus());
                    return existing;
                })
                .orElseGet(() -> {
                    Ride ride = Ride.builder()
                            .id(uuid)
                            .customerId(event.getCustomerId())
                            .pickupLat(event.pickupLat())
                            .pickupLng(event.pickupLng())
                            .dropoffLat(event.dropoffLat())
                            .dropoffLng(event.dropoffLng())
                            .status(RideStatus.CREATED)
                            .build();
                    Ride saved = rideRepository.save(ride);
                    log.info("[RideService] Ride created from booking.created: rideId={} customerId={}",
                            rideId, event.getCustomerId());
                    return saved;
                });
    }

    @Transactional
    public Ride updateRideStatus(String rideId, RideStatus newStatus) {
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Ride not found: " + rideId));

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
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Ride not found: " + rideId));

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
        Ride ride = rideRepository.findById(parseUuid(event.aggregateId()))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Ride not found: " + event.aggregateId()));

        if (ride.getStatus() == RideStatus.ACCEPTED) {
            log.info("[RideService] Duplicate driver.accepted ignored: rideId={}", event.aggregateId());
            return ride;
        }
        if (ride.getStatus() != RideStatus.ASSIGNED) {
            log.warn("[RideService] Invalid driver.accepted transition: rideId={} | current={}",
                    event.aggregateId(), ride.getStatus());
            return ride;
        }
        if (!driverMatches(ride, event.getDriverId())) {
            log.warn("[RideService] Driver mismatch on accept: rideId={} | assigned={} | event={}",
                    event.aggregateId(), ride.getDriverId(), event.getDriverId());
            return ride;
        }

        ride.setStatus(RideStatus.ACCEPTED);
        Ride saved = rideRepository.save(ride);
        log.info("[RideService] Driver accepted ride: rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        return saved;
    }

    @Transactional
    public Ride transitionRideLifecycle(String rideId, String driverId, RideStatus nextStatus, String sourceTopic) {
        UUID uuid = parseUuid(rideId);
        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Ride not found: " + rideId));

        if (ride.getStatus() == nextStatus) {
            log.info("[RideService] Duplicate {} ignored: rideId={} already {}", sourceTopic, rideId, nextStatus);
            return ride;
        }
        if (!isValidLifecycleTransition(ride.getStatus(), nextStatus)) {
            log.warn("[RideService] Invalid {} transition: rideId={} | current={} | next={}",
                    sourceTopic, rideId, ride.getStatus(), nextStatus);
            return ride;
        }
        if (driverId != null && !driverId.isBlank()) {
            if (ride.getDriverId() != null && !ride.getDriverId().equals(driverId)) {
                log.warn("[RideService] Driver mismatch: rideId={} | assigned={} | event={}",
                        rideId, ride.getDriverId(), driverId);
                return ride;
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
                    .eventType("RideArrived")
                    .rideId(rideId)
                    .bookingId(rideId)
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
                    .eventType("RideStarted")
                    .rideId(rideId)
                    .bookingId(rideId)
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

            RideCompletedEvent event = RideCompletedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RideCompleted")
                    .type("RideCompleted")
                    .rideId(rideId)
                    .bookingId(rideId)
                    .driverId(driverId)
                    .customerId(saved.getCustomerId())
                    .finalFare(finalFare)
                    .paymentMethod(paymentMethod)
                    .timestamp(Instant.now().toString())
                    .build();
            kafkaTemplate.send(TOPIC_RIDE_COMPLETED, rideId, event);
            log.info("[RideService] ride.completed published | rideId={} | driverId={}", rideId, driverId);
            publishLegacyRideFinished(saved, event);
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
            throw new IllegalArgumentException("Invalid rideId format: " + rideId, ex);
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

    private RideStatus currentStatus(String rideId) {
        return rideRepository.findById(parseUuid(rideId))
                .map(Ride::getStatus)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Ride not found: " + rideId));
    }

    private boolean driverMatches(Ride ride, String driverId) {
        return driverId != null && !driverId.isBlank() && driverId.equals(ride.getDriverId());
    }

    private void publishLegacyRideFinished(Ride ride, RideCompletedEvent event) {
        RideFinishedEvent legacyEvent = RideFinishedEvent.builder()
                .eventId(event.getEventId())
                .type(RideFinishedEvent.EVENT_TYPE)
                .rideId(event.aggregateId())
                .customerId(ride.getCustomerId())
                .finalFare(event.getFinalFare() == null ? BigDecimal.ZERO : event.getFinalFare())
                .paymentMethod(event.getPaymentMethod() == null ? "CASH" : event.getPaymentMethod())
                .timestamp(Instant.now().toString())
                .build();
        kafkaTemplate.send(TOPIC_RIDE_FINISHED_LEGACY, event.aggregateId(), legacyEvent);
        log.info("[RideService] DEPRECATED compatibility event ride.finished published | rideId={}", event.aggregateId());
    }
}
