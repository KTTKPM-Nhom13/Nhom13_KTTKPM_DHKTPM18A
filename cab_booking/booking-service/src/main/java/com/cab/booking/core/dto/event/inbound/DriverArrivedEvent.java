package com.cab.booking.core.dto.event.inbound;

import java.time.Instant;

public record DriverArrivedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String customerId,
        String driverId
) {
}
