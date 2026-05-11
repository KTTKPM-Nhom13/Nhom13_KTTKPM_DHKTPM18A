package com.cab.booking.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment completed event — nhận từ Payment Service qua Kafka.
 * Topic: payment.completed
 * Consumer: Booking Service (cập nhật trạng thái → PAID)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    public static final String EVENT_TYPE = "PaymentCompleted";

    private String eventId;
    private String type;
    private String rideId;
    private String timestamp;
}
