package com.cab.booking.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideAssignedEvent {
    private String eventId;
    private String type;
    private String eventType;
    private String rideId;
    private String driverId;
    private String driverName;
    private Double driverLat;
    private Double driverLng;
    private String timestamp;
}
