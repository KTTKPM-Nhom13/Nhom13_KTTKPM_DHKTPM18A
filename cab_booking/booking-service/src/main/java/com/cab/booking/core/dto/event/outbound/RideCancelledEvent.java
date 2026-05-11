package com.cab.booking.core.dto.event.outbound;

import java.time.Instant;

public record RideCancelledEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String bookingId,
        String customerId,
        String driverId,
        String reason
) {
    public static RideCancelledEvent create(String bookingId, String customerId, String driverId, String reason) {
        return new RideCancelledEvent(
                "RIDE_CANCELLED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                bookingId,
                customerId,
                driverId,
                reason
        );
    }
}
