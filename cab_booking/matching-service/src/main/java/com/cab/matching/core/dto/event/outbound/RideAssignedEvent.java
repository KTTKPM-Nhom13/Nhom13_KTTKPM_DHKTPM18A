package com.cab.matching.core.dto.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideAssignedEvent {
    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String timestamp;
}
