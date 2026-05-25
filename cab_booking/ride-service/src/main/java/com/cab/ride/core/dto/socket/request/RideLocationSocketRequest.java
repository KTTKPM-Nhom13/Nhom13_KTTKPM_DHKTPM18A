package com.cab.ride.core.dto.socket.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for {@code driver.location.update} event from client.
 * driverId is NOT accepted from client — it is extracted from JWT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideLocationSocketRequest {

    private String rideId;
    private double lat;
    private double lng;
    private Double heading;
    private Double speed;
}
