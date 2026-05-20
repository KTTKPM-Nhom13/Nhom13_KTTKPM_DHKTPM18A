package com.cab.booking.core.service;

import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;

import com.cab.booking.core.dto.event.outbound.BookingTimeoutEvent;

public interface BookingEventPublisher {
    void publishRideCreated(RideCreatedEvent event);
    void publishBookingTimeout(BookingTimeoutEvent event);
    void publishRideCancelled(com.cab.booking.core.entity.Booking booking, String reason);
}
