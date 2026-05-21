package com.cab.booking.core.dto.event.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {

    public static final String EVENT_TYPE = "PaymentFailed";

    private String eventId;
    private String type;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String status;
    private String reason;
    private String timestamp;
}
