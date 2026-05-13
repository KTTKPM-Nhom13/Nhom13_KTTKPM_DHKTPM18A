package com.cab.ride.core.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO nhận tọa độ GPS từ tài xế qua API {@code POST /api/v1/rides/location}.
 * Được thiết kế gọn nhẹ — chỉ chứa 3 trường cần thiết, không đụng đến DB.
 */
@Data
public class LocationUpdateRequest {

    @NotBlank(message = "driverId không được trống")
    private String driverId;

    @NotNull(message = "lat không được null")
    @DecimalMin(value = "-90.0",  message = "lat phải >= -90")
    @DecimalMax(value = "90.0",   message = "lat phải <= 90")
    private Double lat;

    @NotNull(message = "lng không được null")
    @DecimalMin(value = "-180.0", message = "lng phải >= -180")
    @DecimalMax(value = "180.0",  message = "lng phải <= 180")
    private Double lng;
}
