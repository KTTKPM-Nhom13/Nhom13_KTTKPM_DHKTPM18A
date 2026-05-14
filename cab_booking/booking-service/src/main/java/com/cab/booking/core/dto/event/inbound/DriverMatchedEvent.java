package com.cab.booking.core.dto.event.inbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Driver-matched event — sự kiện khi tài xế được ghép với chuyến đi.
 */
public class DriverMatchedEvent {

    private UUID rideId;
    private String driverId;
    private Instant matchedAt;

    public DriverMatchedEvent() {
    }

    public DriverMatchedEvent(UUID rideId, String driverId, Instant matchedAt) {
        this.rideId = rideId;
        this.driverId = driverId;
        this.matchedAt = matchedAt;
    }

    public UUID getRideId() {
        return rideId;
    }

    public void setRideId(UUID rideId) {
        this.rideId = rideId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
    }
}
