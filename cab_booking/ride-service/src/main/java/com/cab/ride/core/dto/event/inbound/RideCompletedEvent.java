package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCompletedEvent {
    private String eventId;
    private String eventType;
    private String type;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String customerId;
    private BigDecimal finalFare;
    private String paymentMethod;
    private String timestamp;

    public String aggregateId() {
        return bookingId != null && !bookingId.isBlank() ? bookingId : rideId;
    }
}
