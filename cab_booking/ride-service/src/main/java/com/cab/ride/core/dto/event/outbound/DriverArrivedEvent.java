package com.cab.ride.core.dto.event.outbound;

import java.time.Instant;

public record DriverArrivedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String customerId,
        String driverId
) {
    public static DriverArrivedEvent create(String rideId, String customerId, String driverId) {
        return new DriverArrivedEvent(
                "DRIVER_ARRIVED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                rideId,
                customerId,
                driverId
        );
    }
}
