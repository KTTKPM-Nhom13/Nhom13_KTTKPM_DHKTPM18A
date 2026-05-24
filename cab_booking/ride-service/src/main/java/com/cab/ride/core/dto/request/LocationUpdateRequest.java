package com.cab.ride.core.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO nhận tọa độ GPS từ tài xế qua REST API {@code POST /api/v1/rides/location}.
 *
 * <p>{@code driverId} is extracted from JWT, NOT from the request body.
 * Optional fields: rideId, heading, speed for full ride-aware updates.
 */
@Data
public class LocationUpdateRequest {

    /** Optional rideId for ride-aware location validation. */
    private String rideId;

    @NotNull(message = "lat không được null")
    @DecimalMin(value = "-90.0",  message = "lat phải >= -90")
    @DecimalMax(value = "90.0",   message = "lat phải <= 90")
    private Double lat;

    @NotNull(message = "lng không được null")
    @DecimalMin(value = "-180.0", message = "lng phải >= -180")
    @DecimalMax(value = "180.0",  message = "lng phải <= 180")
    private Double lng;

    /** Optional heading in degrees (0-360). */
    private Double heading;

    /** Optional speed in km/h. */
    private Double speed;
}
