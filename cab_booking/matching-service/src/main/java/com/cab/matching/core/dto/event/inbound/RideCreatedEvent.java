package com.cab.matching.core.dto.event.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Inbound event published by booking-service when a new ride is created.
 */
public record RideCreatedEvent(
        String eventId,
        String type,
        String rideId,
        String customerId,
        String customerNote,
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
    public String eventType() {
        return type;
    }

    public Double pickupLat() {
        return coordinate(pickup, "lat");
    }

    public Double pickupLng() {
        return coordinate(pickup, "lng");
    }

    public int attemptOrDefault() {
        return matchingAttempt == null || matchingAttempt < 1 ? 1 : matchingAttempt;
    }

    public double searchRadiusKmOrDefault() {
        return searchRadiusKm == null || searchRadiusKm <= 0 ? 3.0 : searchRadiusKm;
    }

    public boolean isRematch() {
        return Boolean.TRUE.equals(rematch);
    }

    private static Double coordinate(Map<String, Double> coordinates, String key) {
        return coordinates == null ? null : coordinates.get(key);
    }
}
