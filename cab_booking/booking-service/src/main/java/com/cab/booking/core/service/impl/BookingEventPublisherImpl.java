package com.cab.booking.core.service.impl;

import com.cab.booking.core.dto.event.outbound.RideAcceptedEvent;
import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;
import com.cab.booking.core.dto.event.outbound.BookingTimeoutEvent;
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
        // Cấu hình gửi message dùng ID của Booking làm Kafka Key để đảm bảo 
        // các event của cùng 1 cuốc xe không bị nhảy sai thứ tự trên Partition.
        kafkaTemplate.send("ride.created", event.rideId(), event);
        log.info("🚀 Published RideCreatedEvent | Topic: ride.created | Key: {}", event.rideId());
    }

    @Override
    public void publishRideAccepted(RideAcceptedEvent event) {
        kafkaTemplate.send("ride.accepted", event.rideId(), event);
        log.info("🚀 Published RideAcceptedEvent | Topic: ride.accepted | Key: {}", event.rideId());
    }

    @Override
    public void publishBookingTimeout(BookingTimeoutEvent event) {
        kafkaTemplate.send("booking.timeout", event.rideId(), event);
        log.info("🚀 Published BookingTimeoutEvent | Topic: booking.timeout | Key: {}", event.rideId());
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
        log.info("🚀 Published RideCancelledEvent | Topic: booking-events | Key: {}", booking.getId());
    }
}
