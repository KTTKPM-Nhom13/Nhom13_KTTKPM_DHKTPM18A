package com.cab.booking.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAcceptedEvent {
    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String timestamp;

    public String aggregateId() {
        return bookingId != null && !bookingId.isBlank() ? bookingId : rideId;
    }
}
