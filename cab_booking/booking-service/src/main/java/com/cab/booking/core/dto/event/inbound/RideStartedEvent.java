package com.cab.booking.core.dto.event.inbound;

import java.time.Instant;

public record RideStartedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String customerId,
        String driverId
) {
}
