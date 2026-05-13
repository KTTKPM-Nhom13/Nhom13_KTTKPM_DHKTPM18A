package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound event từ topic {@code ride.assigned} — được publish bởi matching-service
 * khi một tài xế được chỉ định cho cuốc xe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideAssignedEvent {

    /** ID của cuốc xe (UUID dưới dạng String). */
    private String rideId;

    /** ID của tài xế được chỉ định. */
    private String driverId;
}
