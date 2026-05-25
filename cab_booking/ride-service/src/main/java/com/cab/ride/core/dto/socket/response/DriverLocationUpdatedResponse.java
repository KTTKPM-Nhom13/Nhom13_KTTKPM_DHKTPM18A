package com.cab.ride.core.dto.socket.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server → Client response for {@code driver.location.updated} event.
 * Broadcast to all clients in the ride room after successful location update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationUpdatedResponse {

    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private double lat;
    private double lng;
    private Double heading;
    private Double speed;
    private String timestamp;
}
