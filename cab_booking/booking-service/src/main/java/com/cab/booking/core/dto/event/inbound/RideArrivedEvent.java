package com.cab.booking.core.dto.event.inbound;

import java.time.Instant;

public record RideArrivedEvent(
        String eventType,
        String eventId,
        Instant timestamp,
        String rideId,
        String bookingId,
        String customerId,
        String driverId
) {
    public String aggregateId() {
        return bookingId != null && !bookingId.isBlank() ? bookingId : rideId;
    }
}
