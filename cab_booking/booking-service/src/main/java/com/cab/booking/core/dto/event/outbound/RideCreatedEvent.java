package com.cab.booking.core.dto.event.outbound;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Ride-created event — schema chuẩn cho sự kiện tạo chuyến đi gửi lên Kafka.
 */
@Builder
public record RideCreatedEvent(
        String eventId,
        String type,
        String rideId,
        String customerId,
        String customerNote,
        String pickupAddress,
        String dropoffAddress,
        Map<String, Double> pickup,
        Map<String, Double> dropoff,
        String vehicleType,
        String paymentMethod,
        BigDecimal estimatedFare,
        String promoCode,
        Integer matchingAttempt,
        Double searchRadiusKm,
        Boolean rematch,
        List<String> excludedDriverIds,
        String timestamp
) {
    public static final String EVENT_TYPE = "RideCreated";
}
