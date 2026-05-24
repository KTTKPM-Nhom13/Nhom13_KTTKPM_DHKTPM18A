package com.cab.matching.core.dto.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound event published by matching-service to signal ride cancellation.
 * Consumed by booking-service, notification-service, driver-service, and ride-service.
 *
 * <p>All Kafka domain events must be DTO classes. No Map payloads except logs/debug/internal metadata.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCancelledEvent {

    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String customerId;
    private String driverId;
    private String reason;
    private String timestamp;
}
