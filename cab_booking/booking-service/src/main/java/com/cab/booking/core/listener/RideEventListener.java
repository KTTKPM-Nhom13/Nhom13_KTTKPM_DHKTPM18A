package com.cab.booking.core.listener;

import com.cab.booking.core.dto.event.inbound.DriverArrivedEvent;
import com.cab.booking.core.dto.event.inbound.DriverStatusEvent;
import com.cab.booking.core.dto.event.inbound.PaymentCompletedEvent;
import com.cab.booking.core.dto.event.inbound.PaymentFailedEvent;
import com.cab.booking.core.dto.event.inbound.RideAcceptRequestedEvent;
import com.cab.booking.core.dto.event.inbound.RideAcceptedEvent;
import com.cab.booking.core.dto.event.inbound.RideAssignedEvent;
import com.cab.booking.core.dto.event.inbound.RideCompletedEvent;
import com.cab.booking.core.dto.event.inbound.RideFinishedEvent;
import com.cab.booking.core.dto.event.inbound.RideRejectedEvent;
import com.cab.booking.core.dto.event.inbound.RideRejectRequestedEvent;
import com.cab.booking.core.dto.event.inbound.RideStartedEvent;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.core.service.BookingService;
import com.cab.booking.core.statemachine.BookingStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventListener {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingStateMachine bookingStateMachine;

    @KafkaListener(topics = "ride.assigned", groupId = "booking-service-group")
    @Transactional
    public void handleRideAssigned(RideAssignedEvent event) {
        log.info("Received ride.assigned | payload={}", event);
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

        // Idempotent: Bỏ qua nếu đã ở trạng thái ASSIGNED hoặc ACCEPTED
        if (booking.getStatus() == BookingStatus.ASSIGNED || booking.getStatus() == BookingStatus.ACCEPTED) {
            log.info("Booking {} is already in status {}, ignoring duplicate ride.assigned event", booking.getId(), booking.getStatus());
            return;
        }

        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.MATCHING) {
            log.warn("Booking {} is in status {}, skipping ride.assigned", booking.getId(), booking.getStatus());
            return;
        }

        booking.setAssignedDriverId(event.getDriverId());
        booking.setStatus(BookingStatus.ASSIGNED);
        bookingRepository.save(booking);
        log.info("Booking {} moved to ASSIGNED with driver {}", booking.getId(), event.getDriverId());
    }

    @KafkaListener(topics = "ride.accept.requested", groupId = "booking-service-group")
    public void handleRideAcceptRequested(RideAcceptRequestedEvent event) {
        log.info("[ride.accept.requested] rideId={} | driverId={}", event.getRideId(), event.getDriverId());
        try {
            bookingService.acceptRide(UUID.fromString(event.getRideId()), event.getDriverId());
        } catch (Exception ex) {
            log.error("Error processing ride.accept.requested for rideId={}: {}", event.getRideId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.accepted", groupId = "booking-service-group")
    public void handleRideAccepted(RideAcceptedEvent event) {
        log.info("[ride.accepted] rideId={} | driverId={}", event.getRideId(), event.getDriverId());
        try {
            UUID rideId = UUID.fromString(event.getRideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking == null) {
                log.error("Booking not found: {}", rideId);
                return;
            }
            if (booking.getStatus() != BookingStatus.ASSIGNED) {
                log.info("Booking {} is {}, ignoring ride.accepted", rideId, booking.getStatus());
                return;
            }
            bookingService.acceptRide(rideId, event.getDriverId());
        } catch (Exception ex) {
            log.error("Error processing ride.accepted for rideId={}: {}", event.getRideId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.reject.requested", groupId = "booking-service-group")
    public void handleRideRejectRequested(RideRejectRequestedEvent event) {
        log.info("[ride.reject.requested] rideId={} | driverId={} | reason={}",
                event.getRideId(),
                event.getDriverId(),
                event.getReason());
        try {
            bookingService.rejectAssignedRide(UUID.fromString(event.getRideId()), event.getDriverId(), event.getReason());
        } catch (Exception ex) {
            log.error("Error processing ride.reject.requested for rideId={}: {}", event.getRideId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.rejected", groupId = "booking-service-group")
    public void handleRideRejected(RideRejectedEvent event) {
        log.info("[ride.rejected] rideId={} | driverId={} | reason={}",
                event.getRideId(),
                event.getDriverId(),
                event.getReason());
        try {
            bookingService.rejectAssignedRide(UUID.fromString(event.getRideId()), event.getDriverId(), event.getReason());
        } catch (Exception ex) {
            log.error("Error processing ride.rejected for rideId={}: {}", event.getRideId(), ex.getMessage());
        }
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

        // Driver status events are telemetry/sync only. Booking lifecycle changes come from ride.* events.
    }

    @KafkaListener(topics = "ride.arrived", groupId = "booking-service-group")
    public void handleDriverArrived(DriverArrivedEvent event) {
        log.info("[ride.arrived] rideId={}", event.rideId());
        try {
            UUID rideId = UUID.fromString(event.rideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.ACCEPTED) {
                bookingStateMachine.transitionTo(booking, BookingStatus.PICKUP);
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
                bookingStateMachine.transitionTo(booking, BookingStatus.IN_PROGRESS);
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
                bookingStateMachine.transitionTo(booking, BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                log.info("Booking {} moved to COMPLETED", booking.getId());
            }
        } catch (Exception ex) {
            log.error("Error processing ride.finished: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.completed", groupId = "booking-service-group")
    public void handleRideCompleted(RideCompletedEvent event) {
        log.info("[ride.completed] rideId={}", event.getRideId());
        try {
            UUID rideId = UUID.fromString(event.getRideId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking != null && booking.getStatus() == BookingStatus.IN_PROGRESS) {
                bookingStateMachine.transitionTo(booking, BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                log.info("Booking {} moved to COMPLETED", booking.getId());
            }
        } catch (Exception ex) {
            log.error("Error processing ride.completed: {}", ex.getMessage());
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
    public void handlePaymentFailed(PaymentFailedEvent event) {
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

        log.warn("Payment failed for booking {} (status: {}). Reason: {}",
                booking.getId(),
                booking.getStatus(),
                event.getReason());
    }
}
