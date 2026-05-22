package com.cab.booking.core.service.impl;

import com.cab.booking.common.BookingException;
import com.cab.booking.core.client.PricingClient;
import com.cab.booking.core.dto.event.outbound.PaymentRequestedEvent;
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
    private final PricingClient pricingClient;

    // ================================================================
    // LUỒNG TẠO CHUYẾN ĐI
    // ================================================================
    @Override
    @Transactional
    public BookingResponse createRide(String customerId, String accessToken, BookingRequest request) {
        String idempotencyRedisKey = null;
        boolean idempotencyLockAcquired = false;

        // BƯỚC 1: Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            String key = request.getIdempotencyKey();
            idempotencyRedisKey = IDEMPOTENCY_KEY_PREFIX + key;

            // 1.1 Kiểm tra xem DB đã có booking với key này chưa
            Optional<Booking> existingOpt = bookingRepository.findByIdempotencyKey(key);
            if (existingOpt.isPresent()) {
                log.info("♻️ Idempotency check: Trả về booking đã tồn tại trong DB cho key {}", key);
                return BookingResponse.fromEntity(existingOpt.get());
            }

            // 1.2 Dùng Redis SETNX (Set if not exists) để lock tạm thời request đầu tiên
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(idempotencyRedisKey, "PROCESSING", IDEMPOTENCY_TTL);
            idempotencyLockAcquired = Boolean.TRUE.equals(lockAcquired);
            if (Boolean.FALSE.equals(lockAcquired)) {
                log.info("♻️ Một request khác đang xử lý key {}, chờ một chút và lấy lại từ DB...", key);
                try {
                    Thread.sleep(500); // Đợi DB lưu xong
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return bookingRepository.findByIdempotencyKey(key)
                        .map(BookingResponse::fromEntity)
                        .orElseThrow(() -> new BookingException(com.cab.booking.common.ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING));
            }
        }

        // BƯỚC 2: Verify fare
        PricingClient.PricingConfirmResponse confirmedQuote;
        try {
            confirmedQuote = confirmQuoteBeforeBooking(customerId, request, accessToken);
        } catch (RuntimeException ex) {
            releaseIdempotencyLockAfterFailure(idempotencyRedisKey, idempotencyLockAcquired, ex);
            throw ex;
        }
        BigDecimal verifiedFare = confirmedQuote.getTotalFare();
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
                .estimateId(request.getEstimateId())
                .quoteId(confirmedQuote.getQuoteId() != null ? confirmedQuote.getQuoteId() : request.getQuoteId())
                .quotePayloadHash(request.getQuotePayloadHash())
                .quoteHashAlgorithm(confirmedQuote.getQuoteHashAlgorithm() != null
                        ? confirmedQuote.getQuoteHashAlgorithm()
                        : request.getQuoteHashAlgorithm())
                .quoteExpiresAt(confirmedQuote.getExpiresAt() != null ? confirmedQuote.getExpiresAt() : request.getQuoteExpiresAt())
                .idempotencyKey(request.getIdempotencyKey())
                .status(BookingStatus.CREATED)
                .build();

        // BƯỚC 4: Lưu DB và chuyển trạng thái
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            log.info("♻️ Bắt được DataIntegrityViolationException do trùng key {}. Trả về booking cũ.", request.getIdempotencyKey());
            Booking existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Lỗi tranh chấp dữ liệu IdempotencyKey."));
            return BookingResponse.fromEntity(existing);
        }

        boolean isOnlinePayment = request.getPaymentMethod() != null
                && !request.getPaymentMethod().trim().equalsIgnoreCase("CASH");

        if (isOnlinePayment) {
            // ONLINE (MoMo/VNPay/ZaloPay): dừng ở PENDING_PAYMENT, chờ payment.completed rồi mới matching
            bookingStateMachine.transitionTo(booking, BookingStatus.PENDING_PAYMENT);
            booking = bookingRepository.saveAndFlush(booking);
            PaymentRequestedEvent event = buildPaymentRequestedEvent(booking);
            bookingEventPublisher.publishPaymentRequested(event);
            log.info("⏳ Online payment required — bookingId={} | method={} | Đang chờ thanh toán xác nhận.",
                    booking.getId(), request.getPaymentMethod());
        } else {
            // CASH: chuyển MATCHING và tìm tài xế ngay
            bookingStateMachine.transitionTo(booking, BookingStatus.MATCHING);
            booking = bookingRepository.saveAndFlush(booking);

            // BƯỚC 5: Gửi Kafka event ride.created → matching-service tìm tài xế
            RideCreatedEvent event = buildRideCreatedEvent(booking, request, customerId, vehicleType, verifiedFare);
            bookingEventPublisher.publishRideCreated(event);
            log.info("✅ RideCreated → Kafka | bookingId={} | fare={}", booking.getId(), verifiedFare);

            long timeoutScore = Instant.now().plus(Duration.ofMinutes(3)).toEpochMilli();
            redisTemplate.opsForZSet().add("booking:timeout:queue", booking.getId().toString(), timeoutScore);
        }

        // BƯỚC 6: Cache Redis
        redisTemplate.opsForValue().set("booking:" + booking.getId(), booking, Duration.ofHours(2));

        return BookingResponse.fromEntity(booking);
    }

    private RideCreatedEvent buildRideCreatedEvent(Booking booking, BookingRequest request, String customerId, VehicleType vehicleType, BigDecimal verifiedFare) {
        return RideCreatedEvent.builder()
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
    }

    private PaymentRequestedEvent buildPaymentRequestedEvent(Booking booking) {
        String bookingId = booking.getId().toString();
        return PaymentRequestedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(PaymentRequestedEvent.EVENT_TYPE)
                .bookingId(bookingId)
                .rideId(bookingId)
                .customerId(booking.getCustomerId())
                .amount(booking.getEstimatedFare())
                .paymentMethod(booking.getPaymentMethod())
                .paymentPhase(PaymentRequestedEvent.PRE_TRIP_PHASE)
                .currency("VND")
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Xây dựng RideCreatedEvent từ Booking entity — dùng khi thanh toán online xác nhận xong
     * (BookingLifecycleEventListener sẽ gọi hàm này sau khi nhận payment.completed).
     */
    public RideCreatedEvent buildRideCreatedEventFromBooking(Booking booking) {
        Map<String, Double> pickup = coordinateMap(booking.getPickupLat(), booking.getPickupLng());
        Map<String, Double> dropoff = coordinateMap(booking.getDropoffLat(), booking.getDropoffLng());
        return RideCreatedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .type(RideCreatedEvent.EVENT_TYPE)
                .rideId(booking.getId().toString())
                .customerId(booking.getCustomerId())
                .customerNote(booking.getCustomerNote())
                .pickupAddress(booking.getPickupLocation())
                .dropoffAddress(booking.getDropoffLocation())
                .pickup(pickup)
                .dropoff(dropoff)
                .vehicleType(booking.getVehicleType().name())
                .paymentMethod(booking.getPaymentMethod())
                .estimatedFare(booking.getEstimatedFare())
                .promoCode(booking.getPromoCode())
                .matchingAttempt(1)
                .searchRadiusKm(3.0)
                .rematch(false)
                .excludedDriverIds(java.util.List.of())
                .timestamp(Instant.now().toString())
                .build();
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
    // HELPER — Confirm quote & extract coordinates
    // ================================================================
    private PricingClient.PricingConfirmResponse confirmQuoteBeforeBooking(
            String customerId,
            BookingRequest request,
            String accessToken) {
        if (isBlank(request.getEstimateId()) || isBlank(request.getQuotePayloadHash())) {
            throw new BookingException(
                    com.cab.booking.common.ErrorCode.QUOTE_CONFIRMATION_FAILED,
                    "Thieu estimateId hoac quotePayloadHash. Vui long lay bao gia moi.");
        }

        PricingClient.PricingConfirmResponse confirmedQuote = pricingClient.confirmEstimate(
                request.getEstimateId(),
                request.getQuotePayloadHash(),
                accessToken);

        if (confirmedQuote == null || confirmedQuote.getTotalFare() == null) {
            throw new BookingException(
                    com.cab.booking.common.ErrorCode.QUOTE_CONFIRMATION_FAILED,
                    "Pricing Service khong tra ve gia da xac nhan.");
        }

        if (!isBlank(confirmedQuote.getQuotePayloadHash())
                && !request.getQuotePayloadHash().equalsIgnoreCase(confirmedQuote.getQuotePayloadHash())) {
            log.warn("Security quote mismatch after Pricing confirm - estimateId={}, quoteId={}, userId={}",
                    request.getEstimateId(),
                    confirmedQuote.getQuoteId() != null ? confirmedQuote.getQuoteId() : request.getQuoteId(),
                    customerId);
            throw new BookingException(com.cab.booking.common.ErrorCode.QUOTE_HASH_MISMATCH);
        }

        if (request.getEstimatedFare() != null
                && request.getEstimatedFare().compareTo(confirmedQuote.getTotalFare()) != 0) {
            log.warn("Booking estimatedFare differs from confirmed Pricing fare - estimateId={}, quoteId={}, userId={}, requestFare={}, confirmedFare={}",
                    request.getEstimateId(),
                    confirmedQuote.getQuoteId() != null ? confirmedQuote.getQuoteId() : request.getQuoteId(),
                    customerId,
                    request.getEstimatedFare(),
                    confirmedQuote.getTotalFare());
        }

        log.info("Pricing quote confirmed - estimateId={}, quoteId={}, userId={}, fare={} {}",
                request.getEstimateId(),
                confirmedQuote.getQuoteId() != null ? confirmedQuote.getQuoteId() : request.getQuoteId(),
                customerId,
                confirmedQuote.getTotalFare(),
                confirmedQuote.getCurrency());
        return confirmedQuote;
    }

    private void releaseIdempotencyLockAfterFailure(
            String idempotencyRedisKey,
            boolean idempotencyLockAcquired,
            RuntimeException cause) {
        if (!idempotencyLockAcquired || idempotencyRedisKey == null) {
            return;
        }

        try {
            redisTemplate.delete(idempotencyRedisKey);
            log.info("Released idempotency lock after booking failure - key={}, cause={}",
                    idempotencyRedisKey,
                    cause.getClass().getSimpleName());
        } catch (RuntimeException redisEx) {
            log.warn("Could not release idempotency lock after booking failure - key={}",
                    idempotencyRedisKey,
                    redisEx);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
