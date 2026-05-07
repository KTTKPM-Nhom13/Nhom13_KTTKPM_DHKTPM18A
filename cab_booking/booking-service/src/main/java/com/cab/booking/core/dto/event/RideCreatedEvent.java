package com.cab.booking.core.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Ride-created event — schema chuẩn cho sự kiện tạo chuyến đi gửi lên Kafka.
 * Đảm bảo interface giữa các microservice nhất quán, có schema rõ ràng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCreatedEvent {

    public static final String EVENT_TYPE = "RideCreated";

    private String eventId;
    private String type;
    private String rideId;
    private String customerId;
    private String customerNote;

    private Map<String, Double> pickup;
    private Map<String, Double> dropoff;

    private String vehicleType;
    private String paymentMethod;
    private BigDecimal estimatedFare;
    private String promoCode;

    private String timestamp;
}
