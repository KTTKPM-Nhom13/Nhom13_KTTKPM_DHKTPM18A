package com.cab.booking.core.listener;

import com.cab.booking.core.dto.event.inbound.PaymentCompletedEvent;
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

    // ================================================================
    // ride.assigned — cập nhật MATCHING → ASSIGNED
    // ================================================================
    @KafkaListener(topics = "ride.assigned", groupId = "booking-service-group")
    public void handleRideAssigned(RideAssignedEvent event) {
        log.info("⚡ [ride.assigned] rideId={} | driverId={}",
                event.getRideId(), event.getDriverId());

        UUID rideId;
        try {
            rideId = UUID.fromString(event.getRideId());
        } catch (IllegalArgumentException ex) {
            log.error("❌ rideId '{}' không hợp lệ, bỏ qua.", event.getRideId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("❌ Không tìm thấy booking: {}", rideId);
            return;
        }

        if (booking.getStatus() != BookingStatus.MATCHING) {
            log.warn("⚠️ booking {} đang ở [{}], bỏ qua ride.assigned", booking.getId(), booking.getStatus());
            return;
        }

        booking.setAssignedDriverId(event.getDriverId());
        booking.setStatus(BookingStatus.ASSIGNED);
        bookingRepository.save(booking);
        log.info("✅ booking {} → ASSIGNED | driver={}", booking.getId(), event.getDriverId());
    }

    // ================================================================
    // payment.completed — cập nhật COMPLETED → PAID
    // ================================================================
    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("💰 [payment.completed] rideId={} | eventId={}",
                event.getRideId(), event.getEventId());

        UUID rideId;
        try {
            rideId = UUID.fromString(event.getRideId());
        } catch (IllegalArgumentException ex) {
            log.error("❌ rideId '{}' không hợp lệ, bỏ qua.", event.getRideId());
            return;
        }

        Booking booking = bookingRepository.findById(rideId).orElse(null);
        if (booking == null) {
            log.error("❌ Không tìm thấy booking: {}", rideId);
            return;
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            log.warn("⚠️ booking {} đang ở [{}], bỏ qua payment.completed", booking.getId(), booking.getStatus());
            return;
        }

        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);
        log.info("✅ booking {} → PAID | customer={} | fare={}",
                booking.getId(), booking.getCustomerId(), booking.getEstimatedFare());
    }
}
