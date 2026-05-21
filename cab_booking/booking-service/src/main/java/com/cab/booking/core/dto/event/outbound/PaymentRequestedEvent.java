package com.cab.booking.core.dto.event.outbound;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PaymentRequestedEvent(
        String eventId,
        String eventType,
        String bookingId,
        String rideId,
        String customerId,
        BigDecimal amount,
        String paymentMethod,
        String paymentPhase,
        String currency,
        String timestamp
) {
    public static final String EVENT_TYPE = "PAYMENT_REQUESTED";
    public static final String PRE_TRIP_PHASE = "PRE_TRIP";
}
