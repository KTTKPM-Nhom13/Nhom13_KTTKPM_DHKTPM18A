package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideAssignedEvent {
    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String customerId;
    private String pickupAddress;
    private String dropoffAddress;
    private Map<String, Double> pickup;
    private Map<String, Double> dropoff;
    private String vehicleType;
    private String paymentMethod;
    private BigDecimal estimatedFare;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
