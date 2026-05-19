package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Inbound event tu topic ride.created (booking-service -> ride-service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCreatedEvent {
    private String eventId;
    private String type;
    private String rideId;
    private String bookingId;
    private String customerId;
    private Map<String, Double> pickup;
    private Map<String, Double> dropoff;
    private String vehicleType;
    private String paymentMethod;

    public String aggregateId() {
        return bookingId != null && !bookingId.isBlank() ? bookingId : rideId;
    }

    public Double pickupLat() {
        return coordinate(pickup, "lat");
    }

    public Double pickupLng() {
        return coordinate(pickup, "lng");
    }

    public Double dropoffLat() {
        return coordinate(dropoff, "lat");
    }

    public Double dropoffLng() {
        return coordinate(dropoff, "lng");
    }

    private static Double coordinate(Map<String, Double> coordinates, String key) {
        return coordinates == null ? null : coordinates.get(key);
    }
}
