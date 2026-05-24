package com.cab.booking.core.dto.event.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published by matching-service when all matching attempts are exhausted
 * without finding an available driver. The booking remains in MATCHING status
 * and relies on BookingTimeoutScheduler to cancel after the configured total timeout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
