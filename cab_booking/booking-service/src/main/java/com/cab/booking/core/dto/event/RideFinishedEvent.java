package com.cab.booking.core.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ride finished event — gửi lên Kafka khi tài xế bấm COMPLETE.
 * Topic: ride.finished
 * Consumer: Payment Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideFinishedEvent {

    public static final String EVENT_TYPE = "RideFinished";

    private String eventId;
    private String type;
    private String rideId;
    private String customerId;
    private BigDecimal estimatedFare;
    private String paymentMethod;
    private String timestamp;
}
