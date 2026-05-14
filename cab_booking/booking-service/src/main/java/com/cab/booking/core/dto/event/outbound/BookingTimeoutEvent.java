package com.cab.booking.core.dto.event.outbound;

import lombok.Builder;

@Builder
public record BookingTimeoutEvent(
        String eventId,
        String type,
    String rideId,
        String customerId,
        String reason,
        String timestamp
) {
    public static final String EVENT_TYPE = "BookingTimeout";
}
