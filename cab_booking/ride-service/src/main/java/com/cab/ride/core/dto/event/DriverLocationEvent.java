package com.cab.ride.core.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event payload được bắn lên Kafka topic {@code driver.location.updated}.
 * Consumer (ví dụ: matching-service, tracking-service) sẽ nhận tọa độ thời gian thực của tài xế.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationEvent {

    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private double lat;
    private double lng;
    private Double heading;
    private Double speed;
    /** ISO-8601 UTC timestamp, e.g. "2026-05-23T19:15:00Z". */
    private String timestamp;
}
