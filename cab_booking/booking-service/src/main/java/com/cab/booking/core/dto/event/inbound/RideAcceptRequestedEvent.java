package com.cab.booking.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideAcceptRequestedEvent {
    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private String timestamp;
}
