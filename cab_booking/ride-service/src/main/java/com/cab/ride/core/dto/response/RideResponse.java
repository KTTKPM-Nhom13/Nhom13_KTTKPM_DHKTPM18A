package com.cab.ride.core.dto.response;

import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideResponse {
    private UUID id;
    private String bookingId;
    private String customerId;
    private String driverId;
    private String pickupAddress;
    private String dropoffAddress;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropoffLat;
    private Double dropoffLng;
    private BigDecimal finalFare;
    private String paymentMethod;
    private RideStatus status;
    private String createdAt;
    private String updatedAt;

    public static RideResponse fromEntity(Ride ride) {
        return RideResponse.builder()
                .id(ride.getId())
                .bookingId(ride.getBookingId())
                .customerId(ride.getCustomerId())
                .driverId(ride.getDriverId())
                .pickupAddress(ride.getPickupAddress())
                .dropoffAddress(ride.getDropoffAddress())
                .pickupLat(ride.getPickupLat())
                .pickupLng(ride.getPickupLng())
                .dropoffLat(ride.getDropoffLat())
                .dropoffLng(ride.getDropoffLng())
                .finalFare(ride.getFinalFare())
                .paymentMethod(ride.getPaymentMethod())
                .status(ride.getStatus())
                .createdAt(ride.getCreatedAt() != null ? ride.getCreatedAt().toString() : null)
                .updatedAt(ride.getUpdatedAt() != null ? ride.getUpdatedAt().toString() : null)
                .build();
    }
}