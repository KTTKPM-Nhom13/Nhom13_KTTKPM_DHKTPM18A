package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound event tu topic ride.created (booking-service -> ride-service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCreatedEvent {
    private String eventId;
    private String rideId;
    private String customerId;
    private String paymentMethod;
}
