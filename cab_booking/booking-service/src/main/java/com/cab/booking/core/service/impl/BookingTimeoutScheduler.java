package com.cab.booking.core.service.impl;

import com.cab.booking.core.dto.event.outbound.BookingTimeoutEvent;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.core.service.BookingEventPublisher;
import com.cab.booking.core.statemachine.BookingStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingTimeoutScheduler {

    public static final String TIMEOUT_QUEUE_KEY = "booking:timeout:queue";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";

    @Value("${app.booking.timeout.matching-minutes:5}")
    private long matchingTimeoutMinutes;

    private final RedisTemplate<String, Object> redisTemplate;
    private final BookingRepository bookingRepository;
    private final BookingStateMachine bookingStateMachine;
    private final BookingEventPublisher bookingEventPublisher;

    @Scheduled(fixedRate = 10000) // Runs every 10 seconds
    @Transactional
    public void processBookingTimeouts() {
        long now = Instant.now().toEpochMilli();

        // Lấy tất cả bookingId đã đến hạn timeout (score <= now)
        Set<Object> expiredBookings = redisTemplate.opsForZSet().rangeByScore(TIMEOUT_QUEUE_KEY, 0, now);
        if (expiredBookings == null || expiredBookings.isEmpty()) {
            return;
        }

        for (Object obj : expiredBookings) {
            String bookingIdStr = (String) obj;
            UUID bookingId = UUID.fromString(bookingIdStr);

            bookingRepository.findById(bookingId).ifPresent(booking -> {
                // Chỉ hủy nếu trạng thái vẫn còn đang MATCHING (chưa có tài xế nhận)
                if (booking.getStatus() == BookingStatus.MATCHING) {
                    bookingStateMachine.transitionTo(booking, BookingStatus.CANCELLED);
                    bookingRepository.save(booking);
                    redisTemplate.opsForValue().set(BOOKING_CANCELLED_PREFIX + bookingId, "true");

                    BookingTimeoutEvent event = BookingTimeoutEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .type(BookingTimeoutEvent.EVENT_TYPE)
                            .rideId(bookingIdStr)
                            .customerId(booking.getCustomerId())
                            .reason("TIMEOUT_NO_DRIVER_FOUND")
                            .timestamp(Instant.now().toString())
                            .build();

                    bookingEventPublisher.publishBookingTimeout(event);
                    bookingEventPublisher.publishRideCancelled(booking, "TIMEOUT_NO_DRIVER_FOUND");
                    log.info("🚫 Booking {} CANCELLED due to timeout (no driver found after {} minutes)", bookingId, matchingTimeoutMinutes);
                }
            });

            // Xóa khỏi hàng đợi
            redisTemplate.opsForZSet().remove(TIMEOUT_QUEUE_KEY, obj);
        }
    }
}
