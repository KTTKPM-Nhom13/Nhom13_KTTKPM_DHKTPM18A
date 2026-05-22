package com.cab.booking.core.listener;

import com.cab.booking.core.dto.event.inbound.DriverAcceptedEvent;
import com.cab.booking.core.dto.event.inbound.DriverRejectedEvent;
import com.cab.booking.core.dto.event.inbound.PaymentCompletedEvent;
import com.cab.booking.core.dto.event.inbound.PaymentFailedEvent;
import com.cab.booking.core.dto.event.inbound.RideArrivedEvent;import com.cab.booking.core.dto.event.inbound.RideCancelledEvent;import com.cab.booking.core.dto.event.inbound.RideAssignedEvent;
import com.cab.booking.core.dto.event.inbound.RideCompletedEvent;
import com.cab.booking.core.dto.event.inbound.RideStartedEvent;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.core.service.BookingService;
import com.cab.booking.core.statemachine.BookingStateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLifecycleEventListener {

    private static final Duration PROCESSED_EVENT_TTL = Duration.ofHours(6);
    private static final String PROCESSED_EVENT_PREFIX = "booking:processed-event:";

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingStateMachine bookingStateMachine;
    private final com.cab.booking.core.service.BookingEventPublisher bookingEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "ride.assigned", groupId = "booking-service-group")
    @Transactional
    public void handleRideAssigned(RideAssignedEvent event) {
        handleAssignmentEvent(event);
    }

    @KafkaListener(topics = "ride.accepted", groupId = "booking-service-group")
    @Transactional
    public void handleRideAccepted(DriverAcceptedEvent event) {
        log.info("[ride.accepted] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            if (isDuplicateEvent("ride.accepted", event.getEventId())) {
                return;
            }
            UUID rideId = UUID.fromString(event.aggregateId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking == null) {
                log.error("Booking not found: {}", rideId);
                return;
            }
            if (hasReachedOrPassed(booking.getStatus(), BookingStatus.ACCEPTED)) {
                log.info("Booking {} is already {}, ignoring duplicate/late ride.accepted", rideId, booking.getStatus());
                return;
            }
            if (booking.getStatus() != BookingStatus.ASSIGNED) {
                log.warn("Booking {} is {}, skipping ride.accepted", rideId, booking.getStatus());
                return;
            }
            bookingService.acceptRide(rideId, event.getDriverId());
        } catch (Exception ex) {
            log.error("Error processing ride.accepted for rideId={}: {}", event.aggregateId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.rejected", groupId = "booking-service-group")
    @Transactional
    public void handleRideRejected(DriverRejectedEvent event) {
        log.info("[ride.rejected] rideId={} | driverId={} | reason={}",
                event.aggregateId(),
                event.getDriverId(),
                event.getReason());
        try {
            if (isDuplicateEvent("ride.rejected", event.getEventId())) {
                return;
            }
            UUID rideId = UUID.fromString(event.aggregateId());
            Booking booking = bookingRepository.findById(rideId).orElse(null);
            if (booking == null) {
                log.error("Booking not found: {}", rideId);
                return;
            }
            if (booking.getStatus() == BookingStatus.MATCHING || hasReachedOrPassed(booking.getStatus(), BookingStatus.ACCEPTED)) {
                log.info("Booking {} is {}, ignoring duplicate/late ride.rejected", rideId, booking.getStatus());
                return;
            }
            bookingService.rejectAssignedRide(rideId, event.getDriverId(), event.getReason());
        } catch (Exception ex) {
            log.error("Error processing ride.rejected for rideId={}: {}", event.aggregateId(), ex.getMessage());
        }
    }

    @KafkaListener(topics = "ride.cancelled", groupId = "booking-service-group")
        @Transactional
        public void handleRideCancelled(String message) {
            log.info("[ride.cancelled] message={}", message);
            try {
                RideCancelledEvent event = objectMapper.readValue(message, RideCancelledEvent.class);

                if (isDuplicateEvent("ride.cancelled", event.getEventId())) {
                    return;
                }

                UUID bookingId = UUID.fromString(event.getBookingId() != null ? event.getBookingId() : event.getRideId());
                Booking booking = bookingRepository.findById(bookingId).orElse(null);
                if (booking == null) return;

                if (booking.getStatus() == BookingStatus.CANCELLED || hasReachedOrPassed(booking.getStatus(), BookingStatus.ACCEPTED)) {
                    log.info("Booking {} already {}, ignoring ride.cancelled", bookingId, booking.getStatus());
                    return;
                }

                log.info("Process ride.cancelled from system | bookingId={} | reason={}", bookingId, event.getReason());
                bookingService.cancelRide(bookingId, "System Canceled: " + event.getReason());
            } catch (Exception ex) {
                log.error("Error processing ride.cancelled: {}", ex.getMessage());
            }
        }

    @KafkaListener(topics = "ride.arrived", groupId = "booking-service-group")
    @Transactional
    public void handleRideArrived(RideArrivedEvent event) {
        log.info("[ride.arrived] rideId={}", event.aggregateId());
        if (isDuplicateEvent("ride.arrived", event.eventId())) {
            return;
        }
        transitionIfCurrent(event.aggregateId(), BookingStatus.ACCEPTED, BookingStatus.PICKUP, "ride.arrived");
    }

    @KafkaListener(topics = "ride.started", groupId = "booking-service-group")
    @Transactional
    public void handleRideStarted(RideStartedEvent event) {
        log.info("[ride.started] rideId={}", event.aggregateId());
        if (isDuplicateEvent("ride.started", event.eventId())) {
            return;
        }
        transitionIfCurrent(event.aggregateId(), BookingStatus.PICKUP, BookingStatus.IN_PROGRESS, "ride.started");
    }

    @KafkaListener(topics = "ride.completed", groupId = "booking-service-group")
    @Transactional
    public void handleRideCompleted(RideCompletedEvent event) {
        log.info("[ride.completed] rideId={}", event.aggregateId());
        if (isDuplicateEvent("ride.completed", event.getEventId())) {
            return;
        }
        transitionIfCurrent(event.aggregateId(), BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED, "ride.completed");
    }

    @KafkaListener(
            topics = "payment.completed",
            groupId = "booking-service-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentCompleted(String payload) {
        PaymentCompletedEvent event = readPaymentEvent(payload, PaymentCompletedEvent.class, "payment.completed");
        if (event == null) {
            return;
        }
        if (isDuplicateEvent("payment.completed", event.getEventId())) {
            return;
        }
        log.info("[payment.completed] rideId={} | bookingId={} | eventId={} | amount={}",
                event.getRideId(), event.getBookingId(), event.getEventId(), event.getAmount());

        UUID rideId = resolveRideId(event.getRideId(), event.getBookingId(), "payment.completed");
        if (rideId == null) {
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }

        // ✅ LUỒNG MỚI: thanh toán online trước chuyến → trigger matching
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookingStateMachine.transitionTo(booking, BookingStatus.MATCHING);
            bookingRepository.save(booking);
            log.info("💳 Payment completed, triggering driver matching for booking {}", rideId);

            // Bắn ride.created để matching-service tìm tài xế
            if (bookingService instanceof com.cab.booking.core.service.impl.BookingServiceImpl impl) {
                com.cab.booking.core.dto.event.outbound.RideCreatedEvent rideCreatedEvent = impl.buildRideCreatedEventFromBooking(booking);
                bookingEventPublisher.publishRideCreated(rideCreatedEvent);
            }
            long timeoutScore = Instant.now().plus(Duration.ofMinutes(3)).toEpochMilli();
            redisTemplate.opsForZSet().add("booking:timeout:queue", booking.getId().toString(), timeoutScore);
            return;
        }

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            log.info("Booking {} is already COMPLETED; payment.completed is financial-only and does not change booking status",
                    booking.getId());
            return;
        }

        log.warn("Booking {} is in status {}, skipping payment.completed", booking.getId(), booking.getStatus());
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "booking-service-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(String payload) {
        PaymentFailedEvent event = readPaymentEvent(payload, PaymentFailedEvent.class, "payment.failed");
        if (event == null) {
            return;
        }
        if (isDuplicateEvent("payment.failed", event.getEventId())) {
            return;
        }
        UUID rideId = resolveRideId(event.getRideId(), event.getBookingId(), "payment.failed");
        log.info("[payment.failed] rideId={} | bookingId={} | status={} | reason={}",
                event.getRideId(), event.getBookingId(), event.getStatus(), event.getReason());
        if (rideId == null) {
            return;
        }
        
        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.warn("Booking not found for payment.failed | rideId={} | reason={}", rideId, event.getReason());
            return;
        }

        // ✅ THÊM MỚI: Hủy booking nếu đang chờ thanh toán
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            log.warn("❌ Payment failed, booking {} CANCELLED. Reason: {}", rideId, event.getReason());
            bookingService.cancelRide(rideId, "Thanh toán thất bại: " + event.getReason());
        }
    }

    private void handleAssignmentEvent(RideAssignedEvent event) {
        log.info("[ride.assigned] rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        if (isDuplicateEvent("ride.assigned", event.getEventId())) {
            return;
        }

        UUID rideId;
        try {
            rideId = UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId '{}', skipping event.", event.aggregateId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }

        if (booking.getStatus() == BookingStatus.ASSIGNED || booking.getStatus() == BookingStatus.ACCEPTED) {
            log.info("Booking {} is already {}, ignoring duplicate assignment", booking.getId(), booking.getStatus());
            return;
        }
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.MATCHING) {
            log.warn("Booking {} is in status {}, skipping assignment", booking.getId(), booking.getStatus());
            return;
        }

        booking.setAssignedDriverId(event.getDriverId());
        bookingStateMachine.transitionTo(booking, BookingStatus.ASSIGNED);
        bookingRepository.save(booking);
        log.info("Booking {} moved to ASSIGNED with driver {}", booking.getId(), event.getDriverId());
    }

    private void transitionIfCurrent(String rawRideId, BookingStatus expected, BookingStatus next, String topic) {
        UUID rideId;
        try {
            rideId = UUID.fromString(rawRideId);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId '{}', skipping {}.", rawRideId, topic);
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("Booking not found: {}", rideId);
            return;
        }
        if (hasReachedOrPassed(booking.getStatus(), next)) {
            log.info("Booking {} is already {}, ignoring duplicate/late {}", booking.getId(), booking.getStatus(), topic);
            return;
        }
        if (booking.getStatus() != expected) {
            log.warn("Booking {} is in status {}, skipping {}", booking.getId(), booking.getStatus(), topic);
            return;
        }

        bookingStateMachine.transitionTo(booking, next);
        bookingRepository.save(booking);
        log.info("Booking {} moved to {}", booking.getId(), next);
    }

    private boolean hasReachedOrPassed(BookingStatus current, BookingStatus target) {
        return statusRank(current) >= statusRank(target);
    }

    private UUID resolveRideId(String rideId, String bookingId, String topic) {
        String id = rideId != null && !rideId.isBlank() ? rideId : bookingId;
        if (id == null || id.isBlank()) {
            log.error("{} event has no rideId/bookingId, skipping.", topic);
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid rideId/bookingId '{}' from {}, skipping event.", id, topic);
            return null;
        }
    }

    private <T> T readPaymentEvent(String payload, Class<T> eventType, String topic) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException ex) {
            log.error("Invalid {} payload, skipping. payload={} | error={}", topic, payload, ex.getMessage());
            return null;
        }
    }

    private int statusRank(BookingStatus status) {
        return switch (status) {
            case CREATED -> 0;
            case PENDING_PAYMENT -> 1;
            case MATCHING -> 2;
            case ASSIGNED -> 3;
            case ACCEPTED -> 4;
            case PICKUP -> 5;
            case IN_PROGRESS -> 6;
            case COMPLETED -> 7;
            case CANCELLED -> 99;
        };
    }

    private boolean isDuplicateEvent(String topic, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        String key = PROCESSED_EVENT_PREFIX + topic + ":" + eventId;
        Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(key, "true", PROCESSED_EVENT_TTL);
        if (Boolean.FALSE.equals(firstSeen)) {
            log.info("Duplicate {} event skipped | eventId={}", topic, eventId);
            return true;
        }
        return false;
    }
}
