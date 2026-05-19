package com.cab.booking.core.service.impl;

import com.cab.booking.core.dto.event.outbound.BookingTimeoutEvent;
import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;
import com.cab.booking.core.service.BookingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisherImpl implements BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishRideCreated(RideCreatedEvent event) {
        kafkaTemplate.send("booking.created", event.rideId(), event);
        kafkaTemplate.send("ride.created", event.rideId(), event);
        log.info("Published booking.created and legacy ride.created | key={}", event.rideId());
    }

    @Override
    public void publishBookingTimeout(BookingTimeoutEvent event) {
        kafkaTemplate.send("booking.timeout", event.rideId(), event);
        log.info("Published booking.timeout | key={}", event.rideId());
    }

    @Override
    public void publishRideCancelled(com.cab.booking.core.entity.Booking booking, String reason) {
        com.cab.booking.core.dto.event.outbound.RideCancelledEvent event = com.cab.booking.core.dto.event.outbound.RideCancelledEvent.create(
                booking.getId().toString(),
                booking.getCustomerId(),
                booking.getAssignedDriverId(),
                reason
        );
        kafkaTemplate.send("ride.cancelled", booking.getId().toString(), event);
        kafkaTemplate.send("booking-events", booking.getId().toString(), event);
        log.info("Published ride.cancelled | key={}", booking.getId());
    }
}
