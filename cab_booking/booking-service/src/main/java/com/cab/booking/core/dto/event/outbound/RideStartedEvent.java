package com.cab.booking.core.dto.event.outbound;

import java.time.Instant;

public record RideStartedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String bookingId,
        String customerId,
        String driverId
) {
    public static RideStartedEvent create(String bookingId, String customerId, String driverId) {
        return new RideStartedEvent(
                "RIDE_STARTED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                bookingId,
                customerId,
                driverId
        );
    }
}
