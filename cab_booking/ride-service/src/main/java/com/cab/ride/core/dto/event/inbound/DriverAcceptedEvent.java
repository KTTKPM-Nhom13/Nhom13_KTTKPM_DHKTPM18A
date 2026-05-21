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
public class DriverAcceptedEvent {
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
    private Double pickupLat;
    private Double pickupLng;
    private Double dropoffLat;
    private Double dropoffLng;
    private BigDecimal finalFare;
    private String paymentMethod;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
