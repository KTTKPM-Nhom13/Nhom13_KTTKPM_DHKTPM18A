package com.cab.booking.core.service.impl;

import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;
import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.enums.VehicleType;
import com.cab.booking.core.enums.VehicleTypeNormalizer;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.core.service.BookingEventPublisher;
import com.cab.booking.core.service.BookingService;
import com.cab.booking.core.statemachine.BookingStateMachine;
import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "booking:idempotency:";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final BookingRepository bookingRepository;
    private final BookingStateMachine bookingStateMachine;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BookingEventPublisher bookingEventPublisher;

    // ================================================================
    // LUỒNG TẠO CHUYẾN ĐI
    // ================================================================
    @Override
    @Transactional
    public BookingResponse createRide(String customerId, BookingRequest request) {

        // BƯỚC 1: Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            String key = request.getIdempotencyKey();

            // 1.1 Kiểm tra xem DB đã có booking với key này chưa
            Optional<Booking> existingOpt = bookingRepository.findByIdempotencyKey(key);
            if (existingOpt.isPresent()) {
                log.info("♻️ Idempotency check: Trả về booking đã tồn tại trong DB cho key {}", key);
                return BookingResponse.fromEntity(existingOpt.get());
            }

            // 1.2 Dùng Redis SETNX (Set if not exists) để lock tạm thời request đầu tiên
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(IDEMPOTENCY_KEY_PREFIX + key, "PROCESSING", IDEMPOTENCY_TTL);
            if (Boolean.FALSE.equals(lockAcquired)) {
                log.info("♻️ Một request khác đang xử lý key {}, chờ một chút và lấy lại từ DB...", key);
                try {
                    Thread.sleep(500); // Đợi DB lưu xong
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return bookingRepository.findByIdempotencyKey(key)
                        .map(BookingResponse::fromEntity)
                        .orElseThrow(() -> new IllegalStateException("Hệ thống đang bận xử lý request này. Vui lòng thử lại sau!"));
            }
        }

        // BƯỚC 2: Verify fare
        BigDecimal verifiedFare = verifyAndExtractFare(request);
        VehicleType vehicleType = normalizeRequestedVehicleType(request.getVehicleType());

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
                .vehicleType(vehicleType)
                .paymentMethod(request.getPaymentMethod())
                .estimatedFare(verifiedFare)
                .promoCode(request.getPromoCode())
                .quoteToken(request.getQuoteToken())
                .idempotencyKey(request.getIdempotencyKey())
                .status(BookingStatus.CREATED)
                .build();

        // BƯỚC 4: Lưu DB → CREATED → MATCHING
        try {
            booking = bookingRepository.saveAndFlush(booking); // Dùng saveAndFlush để kích hoạt Unique Constraint ngay lập tức
        } catch (DataIntegrityViolationException ex) {
            log.info("♻️ Bắt được DataIntegrityViolationException do trùng key {}. Trả về booking cũ.", request.getIdempotencyKey());
            Booking existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Lỗi tranh chấp dữ liệu IdempotencyKey."));
            return BookingResponse.fromEntity(existing);
        }
        bookingStateMachine.transitionTo(booking, BookingStatus.MATCHING);
        booking = bookingRepository.saveAndFlush(booking);

        // BƯỚC 5: Gửi Kafka event
        RideCreatedEvent event = RideCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideCreatedEvent.EVENT_TYPE)
                .rideId(booking.getId().toString())
                .customerId(customerId)
                .customerNote(booking.getCustomerNote())
                .pickupAddress(booking.getPickupLocation())
                .dropoffAddress(booking.getDropoffLocation())
                .pickup(request.getPickupCoordinates())
                .dropoff(request.getDropoffCoordinates())
                .vehicleType(vehicleType.name())
                .paymentMethod(request.getPaymentMethod())
                .estimatedFare(verifiedFare)
                .promoCode(request.getPromoCode())
                .matchingAttempt(1)
                .searchRadiusKm(3.0)
                .rematch(false)
                .excludedDriverIds(java.util.List.of())
                .timestamp(Instant.now().toString())
                .build();
        bookingEventPublisher.publishRideCreated(event);
        log.info("✅ RideCreated → Kafka | bookingId={} | fare={}", booking.getId(), verifiedFare);

        // BƯỚC 6: Cache Redis
        redisTemplate.opsForValue().set("booking:" + booking.getId(), booking, Duration.ofHours(2));

        // BƯỚC 7: Push vào Timeout Queue (Redis ZSet) để chờ xử lý nếu sau 3 phút không có tài xế nhận
        long timeoutScore = Instant.now().plus(Duration.ofMinutes(3)).toEpochMilli();
        redisTemplate.opsForZSet().add("booking:timeout:queue", booking.getId().toString(), timeoutScore);

        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // DRIVER ACCEPT RIDE — ASSIGNED → ACCEPTED
    // ================================================================
    @Override
    @Transactional
    public BookingResponse acceptRide(UUID bookingId, String driverId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (booking.getStatus() != BookingStatus.ASSIGNED) {
            throw new AppException(ErrorCode.INVALID_STATE);
        }

        if (booking.getAssignedDriverId() == null || !booking.getAssignedDriverId().equals(driverId)) {
            log.warn("Driver {} attempted to accept booking {} assigned to {}", driverId, bookingId, booking.getAssignedDriverId());
            throw new IllegalArgumentException("Tài xế không có quyền nhận cuốc xe này.");
        }

        // Dùng State Machine để validate và đổi trạng thái sang ACCEPTED
        bookingStateMachine.transitionTo(booking, BookingStatus.ACCEPTED);

        booking = bookingRepository.save(booking);
        log.info("✅ Driver {} accepted booking {}", driverId, bookingId);

        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        return BookingResponse.fromEntity(booking);
    }

    @Override
    @Transactional
    public BookingResponse rejectAssignedRide(UUID bookingId, String driverId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (booking.getStatus() != BookingStatus.ASSIGNED) {
            throw new AppException(ErrorCode.INVALID_STATE);
        }

        if (booking.getAssignedDriverId() == null || !booking.getAssignedDriverId().equals(driverId)) {
            log.warn("Driver {} attempted to reject booking {} assigned to {}", driverId, bookingId, booking.getAssignedDriverId());
            throw new IllegalArgumentException("Tai xe khong co quyen tu choi cuoc xe nay.");
        }

        booking.setAssignedDriverId(null);
        bookingStateMachine.transitionTo(booking, BookingStatus.MATCHING);
        booking = bookingRepository.save(booking);
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        log.info("Driver {} rejected booking {}. Booking moved back to MATCHING; matching-service rematches from ride.rejected. Reason={}",
                driverId,
                bookingId,
                reason);
        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // START RIDE — PICKUP → IN_PROGRESS
    // ================================================================
    @Deprecated
    @Transactional
    public BookingResponse startRide(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking: " + bookingId));

        if (booking.getStatus() != BookingStatus.PICKUP) {
            throw new IllegalStateException(
                    "Không thể bắt đầu chuyến đi: trạng thái hiện tại là [" + booking.getStatus()
                            + "], yêu cầu PICKUP.");
        }

        bookingStateMachine.transitionTo(booking, BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        log.info("✅ Ride started | bookingId={} | driver={} | customer={}",
                booking.getId(), booking.getAssignedDriverId(), booking.getCustomerId());

        return BookingResponse.fromEntity(booking);
    }

    // ================================================================
    // COMPLETE RIDE - IN_PROGRESS -> COMPLETED
    // ================================================================
    @Deprecated
    @Transactional
    public BookingResponse completeRide(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking: " + bookingId));

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Không thể hoàn thành chuyến đi: trạng thái hiện tại là [" + booking.getStatus()
                            + "], yêu cầu IN_PROGRESS.");
        }

        bookingStateMachine.transitionTo(booking, BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);

        log.info("✅ RideCompleted | bookingId={} | finalFare={} | payment={}",
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
        if (request.getEstimatedFare() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        return request.getEstimatedFare();
    }

    private VehicleType normalizeRequestedVehicleType(String rawVehicleType) {
        try {
            return VehicleTypeNormalizer.normalizeVehicleType(rawVehicleType);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid vehicleType in booking request: {}", rawVehicleType);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Double extractLat(Map<String, Double> coords) {
        return coords != null ? coords.get("lat") : null;
    }

    private Double extractLng(Map<String, Double> coords) {
        return coords != null ? coords.get("lng") : null;
    }

    private Map<String, Double> coordinateMap(Double lat, Double lng) {
        Map<String, Double> coordinates = new java.util.HashMap<>();
        coordinates.put("lat", lat);
        coordinates.put("lng", lng);
        return coordinates;
    }

    // ================================================================
    // CÁC API BỔ SUNG CHO KHÁCH HÀNG & TÀI XẾ
    // ================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getCustomerHistory(String customerId, int page, int size) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size))
                .map(BookingResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getActiveBookingByCustomer(String customerId) {
        java.util.List<BookingStatus> activeStatuses = java.util.List.of(
                BookingStatus.MATCHING, 
                BookingStatus.ASSIGNED, 
                BookingStatus.ACCEPTED,
                BookingStatus.PICKUP, 
                BookingStatus.IN_PROGRESS
        );
        return bookingRepository.findFirstByCustomerIdAndStatusIn(customerId, activeStatuses)
                .map(BookingResponse::fromEntity)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    @Override
    @Transactional
    public BookingResponse cancelRide(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        bookingStateMachine.transitionTo(booking, BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));
        redisTemplate.opsForValue().set(BOOKING_CANCELLED_PREFIX + bookingId, "true", Duration.ofHours(2));

        // TODO: Cần kiểm tra xem có tài xế chưa, nếu có thì có logic phạt phí huỷ không? (Optional)
        bookingEventPublisher.publishRideCancelled(booking, reason);

        log.info("✅ Ride cancelled | bookingId={} | reason={}", bookingId, reason);
        return BookingResponse.fromEntity(booking);
    }

    @Deprecated
    @Transactional
    public BookingResponse arriveAtPickup(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new AppException(ErrorCode.INVALID_STATE);
        }

        bookingStateMachine.transitionTo(booking, BookingStatus.PICKUP);
        booking = bookingRepository.save(booking);
        redisTemplate.opsForValue().set("booking:" + bookingId, booking, Duration.ofHours(2));

        log.info("✅ Driver arrived at pickup for bookingId={}", bookingId);
        return BookingResponse.fromEntity(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getDriverHistory(String driverId, int page, int size) {
        return bookingRepository.findByAssignedDriverIdOrderByCreatedAtDesc(driverId, PageRequest.of(page, size))
                .map(BookingResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(BookingResponse::fromEntity)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<BookingResponse> getNearbyMatchingBookings(double lat, double lng, double radiusKm) {
        // TODO: Kết hợp Redis GeoHash để tìm theo tọa độ thực tế.
        // Hiện tại trả về tất cả các booking đang MATCHING.
        return bookingRepository.findByStatus(BookingStatus.MATCHING).stream()
                .map(BookingResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }

}
