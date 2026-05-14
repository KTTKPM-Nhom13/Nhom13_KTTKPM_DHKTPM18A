package com.cab.ride.core.dto.event.outbound;

import java.time.Instant;

public record RideStartedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String customerId,
        String driverId
) {
    public static RideStartedEvent create(String rideId, String customerId, String driverId) {
        return new RideStartedEvent(
                "RIDE_STARTED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                rideId,
                customerId,
                driverId
        );
    }
}
