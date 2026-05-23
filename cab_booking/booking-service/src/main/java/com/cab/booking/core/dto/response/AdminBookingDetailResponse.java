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
public class AdminBookingDetailResponse {
    private UUID bookingId;
    private String customerId;
    private String driverId;
    private BookingStatus status;
    private String pickupLocation;
    private String dropoffLocation;
    private String customerNote;
    private Map<String, Double> pickupCoordinates;
    private Map<String, Double> dropoffCoordinates;
    private String vehicleType;
    private String paymentMethod;
    private BigDecimal estimatedFare;
    private String promoCode;
    private String estimateId;
    private String quoteId;
    private LocalDateTime quoteExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AdminUserInfoResponse customerInfo;
    private AdminUserInfoResponse driverInfo;

    public static AdminBookingDetailResponse fromEntity(
            Booking booking,
            AdminUserInfoResponse customerInfo,
            AdminUserInfoResponse driverInfo) {
        return AdminBookingDetailResponse.builder()
                .bookingId(booking.getId())
                .customerId(booking.getCustomerId())
                .driverId(booking.getAssignedDriverId())
                .status(booking.getStatus())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .customerNote(booking.getCustomerNote())
                .pickupCoordinates(booking.getPickupLat() != null && booking.getPickupLng() != null
                        ? Map.of("lat", booking.getPickupLat(), "lng", booking.getPickupLng())
                        : null)
                .dropoffCoordinates(booking.getDropoffLat() != null && booking.getDropoffLng() != null
                        ? Map.of("lat", booking.getDropoffLat(), "lng", booking.getDropoffLng())
                        : null)
                .vehicleType(booking.getVehicleType() == null ? null : booking.getVehicleType().name())
                .paymentMethod(booking.getPaymentMethod())
                .estimatedFare(booking.getEstimatedFare())
                .promoCode(booking.getPromoCode())
                .estimateId(booking.getEstimateId())
                .quoteId(booking.getQuoteId())
                .quoteExpiresAt(booking.getQuoteExpiresAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .customerInfo(customerInfo)
                .driverInfo(driverInfo)
                .build();
    }
}
