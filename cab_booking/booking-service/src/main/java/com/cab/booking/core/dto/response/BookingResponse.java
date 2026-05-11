package com.cab.booking.core.dto.response;

import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private UUID id;
    private String customerId;
    private String assignedDriverId;
    private String pickupLocation;
    private String dropoffLocation;
    private String customerNote;

    private Map<String, Double> pickupCoordinates;
    private Map<String, Double> dropoffCoordinates;

    private String vehicleType;
    private String paymentMethod;
    private BigDecimal estimatedFare;
    private String promoCode;

    private BookingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookingResponse fromEntity(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .customerId(booking.getCustomerId())
                .assignedDriverId(booking.getAssignedDriverId())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .customerNote(booking.getCustomerNote())
                .pickupCoordinates(booking.getPickupLat() != null && booking.getPickupLng() != null
                        ? Map.of("lat", booking.getPickupLat(), "lng", booking.getPickupLng())
                        : null)
                .dropoffCoordinates(booking.getDropoffLat() != null && booking.getDropoffLng() != null
                        ? Map.of("lat", booking.getDropoffLat(), "lng", booking.getDropoffLng())
                        : null)
                .vehicleType(booking.getVehicleType())
                .paymentMethod(booking.getPaymentMethod())
                .estimatedFare(booking.getEstimatedFare())
                .promoCode(booking.getPromoCode())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
