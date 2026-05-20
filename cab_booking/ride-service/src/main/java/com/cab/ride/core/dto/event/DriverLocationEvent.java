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

    /** ID của tài xế. */
    private String eventId;

    private String eventType;

    private String rideId;

    private String driverId;

    /** Vĩ độ hiện tại. */
    private double lat;

    /** Kinh độ hiện tại. */
    private double lng;

    /** Thời điểm ghi nhận (epoch milliseconds) — phục vụ dedup & ordering phía consumer. */
    private long timestamp;
}
