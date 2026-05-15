package com.cab.booking.core.dto.event.outbound;

import java.time.Instant;

public record RideCancelledEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String bookingId,
        String customerId,
        String driverId,
        String reason
) {
    public static RideCancelledEvent create(String rideId, String customerId, String driverId, String reason) {
        return new RideCancelledEvent(
                "RIDE_CANCELLED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                rideId,
                rideId,
                customerId,
                driverId,
                reason
        );
    }
}
