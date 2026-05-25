package com.cab.matching.core.dto.event.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound event published by matching-service when all matching attempts are exhausted
 * without finding an available driver. The booking remains in MATCHING status
 * and relies on BookingTimeoutScheduler to cancel after the configured total timeout.
 *
 * <p>All Kafka domain events must be DTO classes. No Map payloads except logs/debug/internal metadata.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingFailedEvent {

    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String customerId;
    private int attempt;
    private String reason;
    private String timestamp;
}
