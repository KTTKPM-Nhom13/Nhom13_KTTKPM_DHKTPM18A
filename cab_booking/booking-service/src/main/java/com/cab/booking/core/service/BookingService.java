package com.cab.booking.core.service;

import com.cab.booking.core.dto.event.RideCreatedEvent;
import com.cab.booking.core.dto.event.RideFinishedEvent;
import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.integration.driver.client.DriverFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "booking:idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final BookingRepository bookingRepository;
    @SuppressWarnings("unused")
    private final DriverFeignClient driverFeignClient; // TODO: re-add driver matching call khi Driver Service hoàn thiện
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ================================================================
    // LUỒNG TẠO CHUYẾN ĐI
    // ================================================================
    @Transactional
    public BookingResponse createRide(String customerId, BookingRequest request) {

        // BƯỚC 1: Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            String key = request.getIdempotencyKey();
            Boolean inRedis = redisTemplate.hasKey(IDEMPOTENCY_KEY_PREFIX + key);
            if (Boolean.TRUE.equals(inRedis) || bookingRepository.existsByIdempotencyKey(key)) {
                log.warn("⛔ Duplicate idempotencyKey: {}", key);
                throw new IllegalStateException("Duplicate request: idempotencyKey = " + key);
            }
            redisTemplate.opsForValue().set(IDEMPOTENCY_KEY_PREFIX + key, "PROCESSING", IDEMPOTENCY_TTL);
        }

        // BƯỚC 2: Verify fare
        BigDecimal verifiedFare = verifyAndExtractFare(request);

        // BƯỚC 3: Build entity
        Booking booking = Booking.builder()
                .customerId(customerId)
                .pickupLocation(request.getPickupLocation())
                .dropoffLocation(request.getDropoffLocation())
                .customerNote(request.getCustomerNote())
                .pickupLat(extractLat(request.getPickupCoordinates()))
                .pickupLng(extractLng(request.getPickupCoordinates()))
                .dropoffLat(extractLat(request.getDropoffCoordinates()))
                .dropoffLng(extractLng(request.getDropoffCoordinates()))
                .vehicleType(request.getVehicleType())
                .paymentMethod(request.getPaymentMethod())
                .estimatedFare(verifiedFare)
                .promoCode(request.getPromoCode())
                .quoteToken(request.getQuoteToken())
                .idempotencyKey(request.getIdempotencyKey())
                .status(BookingStatus.CREATED)
                .build();

        // BƯỚC 4: Lưu DB → CREATED → MATCHING
        try {
            booking = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Duplicate booking: idempotencyKey = " + request.getIdempotencyKey());
        }
        booking.setStatus(BookingStatus.MATCHING);
        booking = bookingRepository.save(booking);

        // BƯỚC 5: Gửi Kafka event
        RideCreatedEvent event = RideCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideCreatedEvent.EVENT_TYPE)
                .rideId(booking.getId().toString())
                .customerId(customerId)
                .customerNote(booking.getCustomerNote())
                .pickup(request.getPickupCoordinates())
                .dropoff(request.getDropoffCoordinates())
                .vehicleType(request.getVehicleType())
                .paymentMethod(request.getPaymentMethod())
                .estimatedFare(verifiedFare)
                .promoCode(request.getPromoCode())
                .timestamp(Instant.now().toString())
                .build();
        kafkaTemplate.send("ride.created", event);
        log.info("✅ RideCreated → Kafka | bookingId={} | fare={}", booking.getId(), verifiedFare);

        // BƯỚC 6: Cache Redis
        redisTemplate.opsForValue().set("booking:" + booking.getId(), booking, Duration.ofHours(2));

        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // START RIDE — ASSIGNED → IN_PROGRESS
    // ================================================================
    @Transactional
    public BookingResponse startRide(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking: " + bookingId));

        if (booking.getStatus() != BookingStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Không thể bắt đầu chuyến đi: trạng thái hiện tại là [" + booking.getStatus()
                            + "], yêu cầu ASSIGNED.");
        }

        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        log.info("✅ Ride started | bookingId={} | driver={} | customer={}",
                booking.getId(), booking.getAssignedDriverId(), booking.getCustomerId());

        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // COMPLETE RIDE — IN_PROGRESS → COMPLETED → gửi ride.finished
    // ================================================================
    @Transactional
    public BookingResponse completeRide(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking: " + bookingId));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Không thể hoàn thành chuyến đi: trạng thái hiện tại là [" + booking.getStatus()
                            + "], yêu cầu IN_PROGRESS.");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);

        // Gửi sự kiện ride.finished → Payment Service tính tiền
        RideFinishedEvent event = RideFinishedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideFinishedEvent.EVENT_TYPE)
                .rideId(booking.getId().toString())
                .customerId(booking.getCustomerId())
                .estimatedFare(booking.getEstimatedFare())
                .paymentMethod(booking.getPaymentMethod())
                .timestamp(Instant.now().toString())
                .build();
        kafkaTemplate.send("ride.finished", event);
        log.info("✅ RideCompleted & ride.finished → Kafka | bookingId={} | fare={} | payment={}",
                booking.getId(), booking.getEstimatedFare(), booking.getPaymentMethod());

        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // HELPER — Verify fare & extract coordinates
    // ================================================================
    private BigDecimal verifyAndExtractFare(BookingRequest request) {
        // TODO: verify quoteToken từ Pricing Service
        if (request.getQuoteToken() != null && !request.getQuoteToken().isBlank()) {
            log.debug("🔐 Verify quoteToken: {}", request.getQuoteToken());
        }
        return request.getEstimatedFare() != null
                ? request.getEstimatedFare()
                : BigDecimal.valueOf(50000);
    }

    private Double extractLat(Map<String, Double> coords) {
        return coords != null ? coords.get("lat") : null;
    }

    private Double extractLng(Map<String, Double> coords) {
        return coords != null ? coords.get("lng") : null;
    }
}
