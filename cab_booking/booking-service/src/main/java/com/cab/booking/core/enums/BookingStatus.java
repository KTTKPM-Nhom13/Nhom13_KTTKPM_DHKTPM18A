package com.cab.booking.core.enums;

/**
 * Ride state machine:
 * CREATED -> PENDING_PAYMENT -> MATCHING -> ASSIGNED -> ACCEPTED -> PICKUP -> IN_PROGRESS -> COMPLETED.
 *
 * PENDING_PAYMENT is used only for online prepaid bookings. CASH bookings go from CREATED to MATCHING.
 * Payment status and driver settlement are owned by Payment Service, not BookingStatus.
 */
public enum BookingStatus {
    CREATED,
    PENDING_PAYMENT,
    MATCHING,
    ASSIGNED,
    ACCEPTED,
    PICKUP,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
