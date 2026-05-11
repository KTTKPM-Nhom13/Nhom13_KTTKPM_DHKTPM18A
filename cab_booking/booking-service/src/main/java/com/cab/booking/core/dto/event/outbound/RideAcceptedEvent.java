package com.cab.booking.core.dto.event.outbound;

import com.cab.booking.core.enums.BookingStatus;
import lombok.Builder;

@Builder
public record RideAcceptedEvent(
        String eventId,
        String type,
        String bookingId,
        String customerId,
        String driverId,
        BookingStatus status,
        String timestamp
) {
    public static final String EVENT_TYPE = "RideAccepted";
}
