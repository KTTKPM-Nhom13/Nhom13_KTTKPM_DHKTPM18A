package com.cab.booking.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Drop-off location is required")
    private String dropoffLocation;

    private String customerNote;

    private Map<String, Double> pickupCoordinates;
    private Map<String, Double> dropoffCoordinates;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;
    private String paymentMethod;

    @Positive(message = "Estimated fare must be positive")
    private BigDecimal estimatedFare;

    private String promoCode;

    /**
     * Zero Trust — FE gửi quoteToken từ Pricing Service.
     * BE giải mã & verify trước khi chấp nhận estimatedFare.
     */
    private String quoteToken;

    /**
     * Quote snapshot returned by Pricing-Service when FE gets an estimate.
     * Booking-Service confirms estimateId with quotePayloadHash before creating booking.
     */
    private String estimateId;
    private String quoteId;
    private String quotePayloadHash;
    private String quoteHashAlgorithm;
    private LocalDateTime quoteExpiresAt;

    /**
     * Idempotency key — chống double-click / retry gây trùng cuốc.
     * FE gửi UUID, BE kiểm tra Redis trước khi xử lý.
     */
    private String idempotencyKey;
}
