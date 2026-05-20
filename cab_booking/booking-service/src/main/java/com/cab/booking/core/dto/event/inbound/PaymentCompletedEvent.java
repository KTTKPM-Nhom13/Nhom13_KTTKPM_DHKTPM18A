package com.cab.booking.core.dto.event.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payment completed event from Payment Service.
 * Topic: payment.completed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentCompletedEvent {

    public static final String EVENT_TYPE = "PaymentCompleted";

    private String eventId;
    private String type;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private BigDecimal amount;
    private String currency;
    private String gatewayTransactionId;
    private String paymentMethod;
    private String timestamp;
}
