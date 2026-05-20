package com.cab.ride.core.listener;

import com.cab.ride.core.dto.event.inbound.DriverAcceptedEvent;
import com.cab.ride.core.dto.event.inbound.RideCancelledEvent;
import com.cab.ride.core.dto.event.inbound.RideAssignedEvent;
import com.cab.ride.core.dto.event.inbound.RideCreatedEvent;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventConsumer {

    private static final String GROUP_ID = "ride-service-group";

    private final RideService rideService;

    @KafkaListener(topics = "ride.created", groupId = GROUP_ID)
    public void handleRideCreated(RideCreatedEvent event) {
        log.info("[ride.created] rideId={} | customerId={}", event.aggregateId(), event.getCustomerId());
        try {
            rideService.createRideFromBooking(event);
        } catch (Exception ex) {
            log.error("Failed to create ride from ride.created event rideId={}: {}",
                    event.aggregateId(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "ride.assigned", groupId = GROUP_ID)
    public void handleRideAssigned(RideAssignedEvent event) {
        handleAssigned(event);
    }

    @KafkaListener(topics = "ride.accepted", groupId = GROUP_ID)
    public void handleRideAccepted(DriverAcceptedEvent event) {
        log.info("[ride.accepted] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            rideService.markDriverAccepted(event);
        } catch (Exception ex) {
            log.error("Failed to handle ride.accepted for rideId={}: {}",
                    event.aggregateId(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "ride.cancelled", groupId = GROUP_ID)
    public void handleRideCancelled(RideCancelledEvent event) {
        log.info("[ride.cancelled] rideId={} | driverId={} | reason={}",
                event.aggregateId(), event.getDriverId(), event.getReason());
        try {
            rideService.markRideCancelled(event);
        } catch (Exception ex) {
            log.error("Failed to handle ride.cancelled for rideId={}: {}",
                    event.aggregateId(), ex.getMessage(), ex);
        }
    }

    private void handleAssigned(RideAssignedEvent event) {
        log.info("[ride.assigned] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());

        if (event.aggregateId() == null || event.aggregateId().isBlank()) {
            log.error("Assignment event has no rideId/bookingId, skipping.");
            return;
        }
        if (event.getDriverId() == null || event.getDriverId().isBlank()) {
            log.error("Assignment event has no driverId, skipping rideId={}", event.aggregateId());
            return;
        }

        try {
            rideService.assignDriverToRide(event.aggregateId(), event.getDriverId(), RideStatus.ASSIGNED);
        } catch (Exception ex) {
            log.error("Failed to handle assignment for rideId={}: {}", event.aggregateId(), ex.getMessage(), ex);
        }
    }
}
