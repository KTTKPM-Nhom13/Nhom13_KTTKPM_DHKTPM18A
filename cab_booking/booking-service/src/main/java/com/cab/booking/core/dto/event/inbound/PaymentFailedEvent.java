package com.cab.booking.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {

    public static final String EVENT_TYPE = "PaymentFailed";

    private String eventId;
    private String type;
    private String rideId;
    private String reason;
    private String timestamp;
}
