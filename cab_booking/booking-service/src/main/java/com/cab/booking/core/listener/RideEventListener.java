package com.cab.booking.core.listener;

import com.cab.booking.core.dto.event.DriverStatusEvent;
import com.cab.booking.core.dto.event.inbound.PaymentCompletedEvent;
import com.cab.booking.core.dto.event.inbound.DriverArrivedEvent;
import com.cab.booking.core.dto.event.inbound.RideStartedEvent;
import com.cab.booking.core.dto.event.inbound.RideFinishedEvent;
import com.cab.booking.core.dto.event.outbound.RideAssignedEvent;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventListener {

    private final BookingRepository bookingRepository;

    @KafkaListener(topics = "ride.assigned", groupId = "booking-service-group")
    public void handleRideAssigned(RideAssignedEvent event) {
        log.info("[ride.assigned] rideId={} | driverId={}", event.getRideId(), event.getDriverId());

        UUID rideId;
        try {
            rideId = UUID.fromString(event.getRideId());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId '{}', skipping event.", event.getRideId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }

        if (booking.getStatus() != BookingStatus.MATCHING) {
            log.warn("Booking {} is in status {}, skipping ride.assigned", booking.getId(), booking.getStatus());
            return;
        }

        booking.setAssignedDriverId(event.getDriverId());
        booking.setStatus(BookingStatus.ASSIGNED);
        bookingRepository.save(booking);
        log.info("Booking {} moved to ASSIGNED with driver {}", booking.getId(), event.getDriverId());
    }

    @KafkaListener(topics = "driver.status.changed", groupId = "booking-service-group")
    public void handleDriverStatusChanged(DriverStatusEvent event) {
        log.info(
                "[driver.status.changed] driverId={} | availability={} | activeForBooking={} | rideId={} | rideStatus={}",
                event.getDriverId(),
                event.getAvailabilityStatus(),
                event.getActiveForBooking(),
                event.getRideId(),
                event.getRideStatus());
    }

    @KafkaListener(topics = "ride.arrived", groupId = "booking-service-group")
    public void handleDriverArrived(DriverArrivedEvent event) {
        log.info("[ride.arrived] rideId={}", event.rideId());
        try {
            UUID rideId = UUID.fromString(event.rideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.ASSIGNED) {
                booking.setStatus(BookingStatus.PICKUP);
                bookingRepository.save(booking);
                log.info("Booking {} moved to PICKUP", booking.getId());
            }
        } catch (Exception ex) {
            log.error("Error processing ride.arrived: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.started", groupId = "booking-service-group")
    public void handleRideStarted(RideStartedEvent event) {
        log.info("[ride.started] rideId={}", event.rideId());
        try {
            UUID rideId = UUID.fromString(event.rideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.PICKUP) {
                booking.setStatus(BookingStatus.IN_PROGRESS);
                bookingRepository.save(booking);
                log.info("Booking {} moved to IN_PROGRESS", booking.getId());
            }
        } catch (Exception ex) {
            log.error("Error processing ride.started: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.finished", groupId = "booking-service-group")
    public void handleRideFinished(RideFinishedEvent event) {
        log.info("[ride.finished] rideId={}", event.getRideId());
        try {
            UUID rideId = UUID.fromString(event.getRideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.IN_PROGRESS) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                log.info("Booking {} moved to COMPLETED", booking.getId());
            }
        } catch (Exception ex) {
            log.error("Error processing ride.finished: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[payment.completed] rideId={} | eventId={}", event.getRideId(), event.getEventId());

        UUID rideId;
        try {
            rideId = UUID.fromString(event.getRideId());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId '{}', skipping event.", event.getRideId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            log.warn("Booking {} is in status {}, skipping payment.completed", booking.getId(), booking.getStatus());
            return;
        }

        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);
        log.info("Booking {} moved to PAID | customer={} | fare={}",
                booking.getId(),
                booking.getCustomerId(),
                booking.getEstimatedFare());
    }

    @KafkaListener(topics = "payment.failed", groupId = "booking-service-group")
    public void handlePaymentFailed(com.cab.booking.core.dto.event.inbound.PaymentFailedEvent event) {
        log.info("[payment.failed] rideId={} | reason={}", event.getRideId(), event.getReason());

        UUID rideId;
        try {
            rideId = UUID.fromString(event.getRideId());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId '{}', skipping event.", event.getRideId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }

        // TODO: Xử lý logic nghiệp vụ khi thanh toán lỗi (ví dụ: đòi nợ sau, gửi thông báo)
        log.warn("Payment failed for booking {} (status: {}). Reason: {}", booking.getId(), booking.getStatus(), event.getReason());
    }
}
