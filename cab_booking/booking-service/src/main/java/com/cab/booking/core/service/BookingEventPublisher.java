package com.cab.booking.core.service;

import com.cab.booking.core.dto.event.outbound.RideAcceptedEvent;
import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;

import com.cab.booking.core.dto.event.outbound.BookingTimeoutEvent;

public interface BookingEventPublisher {
    void publishRideCreated(RideCreatedEvent event);
    void publishRideAccepted(RideAcceptedEvent event);
    void publishBookingTimeout(BookingTimeoutEvent event);
    void publishDriverArrived(com.cab.booking.core.entity.Booking booking);
    void publishRideStarted(com.cab.booking.core.entity.Booking booking);
    void publishRideCancelled(com.cab.booking.core.entity.Booking booking, String reason);
}
