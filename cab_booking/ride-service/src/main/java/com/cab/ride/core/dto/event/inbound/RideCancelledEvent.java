package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCancelledEvent {
    private String eventId;
    private String eventType;
    private String type;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String customerId;
    private String reason;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
