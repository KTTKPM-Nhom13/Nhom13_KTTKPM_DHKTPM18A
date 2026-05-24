package com.cab.matching.service.consumer;

import com.cab.matching.core.dto.event.inbound.DriverRejectedEvent;
import com.cab.matching.core.dto.event.inbound.RideCancelledEvent;
import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listeners for matching-service.
 * All listeners use typed DTO signatures — the JsonDeserializer handles
 * deserialization via type mappings configured in application.yml.
 *
 * <p>All Kafka domain events must be DTO classes. No Map payloads except logs/debug/internal metadata.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideCreatedListener {

    private final MatchingService matchingService;

    // ─── ride.created ───────────────────────────────────────────────

    @KafkaListener(topics = "ride.created", groupId = "matching-group")
    public void listenRideCreated(RideCreatedEvent event) {
        log.info("Received ride.created: rideId={} | customerId={}",
                event.rideId(), event.customerId());

        try {
            matchingService.processMatching(event);
        } catch (Exception e) {
            log.error("Critical error processing ride.created for rideId={}: {}",
                    event.rideId(), e.getMessage(), e);
        }
    }

    // ─── matching.retry.requested ───────────────────────────────────

    @KafkaListener(topics = "matching.retry.requested", groupId = "matching-group")
    public void listenMatchingRetryRequested(RideCreatedEvent event) {
        log.info("Received matching.retry.requested: rideId={} | attempt={}",
                event.rideId(), event.attemptOrDefault());

        try {
            matchingService.processMatching(event);
        } catch (Exception e) {
            log.error("Critical error processing matching.retry.requested for rideId={}: {}",
                    event.rideId(), e.getMessage(), e);
        }
    }

    // ─── ride.rejected ──────────────────────────────────────────────

    @KafkaListener(topics = "ride.rejected", groupId = "matching-group")
    public void listenRideRejected(DriverRejectedEvent event) {
        log.info("Received ride.rejected: rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            matchingService.processDriverRejected(event);
        } catch (Exception e) {
            log.error("Failed to rematch rideId={} after driver rejection: {}",
                    event.aggregateId(), e.getMessage(), e);
        }
    }

    // ─── ride.cancelled ─────────────────────────────────────────────

    @KafkaListener(topics = "ride.cancelled", groupId = "matching-group")
    public void listenRideCancelled(RideCancelledEvent event) {
        log.info("Received ride.cancelled: rideId={} | driverId={}", event.getRideId(), event.getDriverId());
        try {
            matchingService.processRideCancelled(event);
        } catch (Exception e) {
            log.error("Failed to cleanup matching state after ride.cancelled rideId={}: {}",
                    event.getRideId(), e.getMessage(), e);
        }
    }
}
