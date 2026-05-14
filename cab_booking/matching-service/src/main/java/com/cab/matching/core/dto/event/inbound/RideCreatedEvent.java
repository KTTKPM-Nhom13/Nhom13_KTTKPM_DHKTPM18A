package com.cab.matching.core.dto.event.inbound;

/**
 * Record đại diện cho event khi một cuốc xe mới được tạo.
 * Sử dụng Java 21 Record để tự động sinh constructor, getters, equals, hashCode và toString.
 */
public record RideCreatedEvent(
        String eventType,
        String eventId,
        String rideId,
        String customerId,
        Double pickupLat,
        Double pickupLng,
        Double estimatedFare,
        String paymentMethod
) {
}
