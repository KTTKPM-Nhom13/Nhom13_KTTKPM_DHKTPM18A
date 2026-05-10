package com.cab.booking.core.dto.event.outbound;

import java.time.Instant;

public record DriverArrivedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String bookingId,
        String customerId,
        String driverId
) {
    public static DriverArrivedEvent create(String bookingId, String customerId, String driverId) {
        return new DriverArrivedEvent(
                "DRIVER_ARRIVED",
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                bookingId,
                customerId,
                driverId
        );
    }
}
