package com.cab.ride.core.dto.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideStartedEvent {
    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String customerId;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
