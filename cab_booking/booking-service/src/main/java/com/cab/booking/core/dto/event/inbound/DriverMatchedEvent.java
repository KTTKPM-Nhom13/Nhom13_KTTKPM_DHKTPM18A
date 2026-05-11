package com.cab.booking.core.dto.event.inbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Driver-matched event — sự kiện khi tài xế được ghép với chuyến đi.
 */
public class DriverMatchedEvent {

    private UUID bookingId;
    private String driverId;
    private Instant matchedAt;

    public DriverMatchedEvent() {
    }

    public DriverMatchedEvent(UUID bookingId, String driverId, Instant matchedAt) {
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.matchedAt = matchedAt;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
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
