package com.cab.booking.core.dto.response;

import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingSummaryResponse {
    private UUID bookingId;
    private String customerId;
    private String driverId;
    private BookingStatus status;
    private String pickupLocation;
    private String dropoffLocation;
    private String vehicleType;
    private String paymentMethod;
    private BigDecimal estimatedFare;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminBookingSummaryResponse fromEntity(Booking booking) {
        return AdminBookingSummaryResponse.builder()
                .bookingId(booking.getId())
                .customerId(booking.getCustomerId())
                .driverId(booking.getAssignedDriverId())
                .status(booking.getStatus())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .vehicleType(booking.getVehicleType() == null ? null : booking.getVehicleType().name())
                .paymentMethod(booking.getPaymentMethod())
                .estimatedFare(booking.getEstimatedFare())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
