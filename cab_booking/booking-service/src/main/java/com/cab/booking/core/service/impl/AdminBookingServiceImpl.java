package com.cab.booking.core.service.impl;

import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import com.cab.booking.core.client.UserServiceClient;
import com.cab.booking.core.dto.event.outbound.RideCreatedEvent;
import com.cab.booking.core.dto.request.AdminCancelBookingRequest;
import com.cab.booking.core.dto.request.AdminUpdateBookingStatusRequest;
import com.cab.booking.core.dto.response.AdminBookingDetailResponse;
import com.cab.booking.core.dto.response.AdminBookingSummaryResponse;
import com.cab.booking.core.dto.response.AdminBookingTimelineResponse;
import com.cab.booking.core.dto.response.AdminUserInfoResponse;
import com.cab.booking.core.entity.AdminBookingAudit;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.enums.VehicleType;
import com.cab.booking.core.repository.AdminBookingAuditRepository;
import com.cab.booking.core.repository.BookingRepository;
import com.cab.booking.core.service.AdminBookingService;
import com.cab.booking.core.service.BookingEventPublisher;
import com.cab.booking.core.statemachine.BookingStateMachine;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookingServiceImpl implements AdminBookingService {
    private static final String TIMEOUT_QUEUE_KEY = "booking:timeout:queue";

    // Matching-service Redis key prefixes (shared Redis instance)
    private static final String MATCHING_ASSIGNED_PREFIX = "matching:assigned:";
    private static final String MATCHING_DRIVER_PREFIX = "matching:driver:";
    private static final String MATCHING_LOCK_PREFIX = "matching:lock:";
    private static final String MATCHING_REQUEST_PREFIX = "matching:request:";
    private static final String MATCHING_FAILED_PREFIX = "matching:failed:";
    private static final String MATCHING_ATTEMPT_PREFIX = "matching:attempt:";
    private static final String MATCHING_COOLDOWN_PREFIX = "matching:cooldown:";
    private static final String RIDE_REJECTED_DRIVERS_KEY_PATTERN = "ride:%s:rejected-drivers";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";

    private final BookingRepository bookingRepository;
    private final AdminBookingAuditRepository auditRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final BookingStateMachine bookingStateMachine;
    private final UserServiceClient userServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminBookingSummaryResponse> searchBookings(
            BookingStatus status,
            String customerId,
            String driverId,
            String paymentMethod,
            VehicleType vehicleType,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable) {
        return bookingRepository.findAll(
                bookingSpecification(status, customerId, driverId, paymentMethod, vehicleType, createdFrom, createdTo),
                pageable).map(AdminBookingSummaryResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBookingDetailResponse getBookingDetail(UUID bookingId, String bearerToken) {
        Booking booking = getRequiredBooking(bookingId);
        return toDetail(booking, bearerToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminBookingTimelineResponse> getTimeline(UUID bookingId) {
        getRequiredBooking(bookingId);
        return auditRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(AdminBookingTimelineResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public AdminBookingDetailResponse cancelBooking(
            UUID bookingId,
            AdminCancelBookingRequest request,
            String adminId,
            String bearerToken) {
        Booking booking = getRequiredBooking(bookingId);
        BookingStatus oldStatus = booking.getStatus();

        if (oldStatus == BookingStatus.CANCELLED) {
            audit(booking, adminId, "ADMIN_CANCEL_DUPLICATE", oldStatus, oldStatus, request.getReason(), null);
            return toDetail(booking, bearerToken);
        }
        if (oldStatus == BookingStatus.COMPLETED || oldStatus == BookingStatus.IN_PROGRESS) {
            throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Cannot admin-cancel booking in status " + oldStatus);
        }

        bookingStateMachine.transitionTo(booking, BookingStatus.CANCELLED);
        Booking saved = bookingRepository.saveAndFlush(booking);
        bookingEventPublisher.publishRideCancelled(saved, "ADMIN_CANCEL: " + request.getReason());
        audit(saved, adminId, "ADMIN_CANCEL", oldStatus, saved.getStatus(), request.getReason(), null);
        return toDetail(saved, bearerToken);
    }

    @Override
    @Transactional
    public AdminBookingDetailResponse retryMatching(UUID bookingId, String adminId, String bearerToken) {
        Booking booking = getRequiredBooking(bookingId);
        BookingStatus oldStatus = booking.getStatus();

        if (oldStatus != BookingStatus.MATCHING && oldStatus != BookingStatus.ASSIGNED) {
            throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Retry matching is allowed only for MATCHING or ASSIGNED booking");
        }

        booking.setAssignedDriverId(null);
        if (oldStatus == BookingStatus.ASSIGNED) {
            bookingStateMachine.transitionTo(booking, BookingStatus.MATCHING);
        }
        Booking saved = bookingRepository.saveAndFlush(booking);
        clearMatchingRedisState(saved.getId().toString());
        publishRideCreated(saved);
        long timeoutScore = Instant.now().plus(Duration.ofMinutes(3)).toEpochMilli();
        redisTemplate.opsForZSet().add(TIMEOUT_QUEUE_KEY, saved.getId().toString(), timeoutScore);
        audit(saved, adminId, "ADMIN_RETRY_MATCHING", oldStatus, saved.getStatus(), "Operational retry matching", null);
        return toDetail(saved, bearerToken);
    }

    @Override
    @Transactional
    public AdminBookingDetailResponse updateStatus(
            UUID bookingId,
            AdminUpdateBookingStatusRequest request,
            String adminId,
            String bearerToken) {
        Booking booking = getRequiredBooking(bookingId);
        BookingStatus oldStatus = booking.getStatus();
        BookingStatus newStatus = request.getStatus();

        if (newStatus == oldStatus) {
            audit(booking, adminId, "ADMIN_FORCE_STATUS_DUPLICATE", oldStatus, newStatus, request.getReason(), null);
            return toDetail(booking, bearerToken);
        }
        if (oldStatus == BookingStatus.COMPLETED || oldStatus == BookingStatus.CANCELLED) {
            throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Cannot force update terminal booking status " + oldStatus);
        }
        if (newStatus == BookingStatus.COMPLETED || newStatus == BookingStatus.IN_PROGRESS) {
            throw new BookingException(ErrorCode.INVALID_REQUEST,
                    "Force update to " + newStatus + " is not allowed by admin API");
        }

        booking.setStatus(newStatus);
        if (newStatus == BookingStatus.MATCHING) {
            booking.setAssignedDriverId(null);
        }
        Booking saved = bookingRepository.saveAndFlush(booking);
        audit(saved, adminId, "ADMIN_FORCE_STATUS", oldStatus, newStatus, request.getReason(), null);
        return toDetail(saved, bearerToken);
    }

    private Specification<Booking> bookingSpecification(
            BookingStatus status,
            String customerId,
            String driverId,
            String paymentMethod,
            VehicleType vehicleType,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hasText(customerId)) {
                predicates.add(cb.equal(root.get("customerId"), customerId.trim()));
            }
            if (hasText(driverId)) {
                predicates.add(cb.equal(root.get("assignedDriverId"), driverId.trim()));
            }
            if (hasText(paymentMethod)) {
                predicates.add(cb.equal(cb.lower(root.get("paymentMethod")), paymentMethod.trim().toLowerCase()));
            }
            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("vehicleType"), vehicleType));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Booking getRequiredBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private AdminBookingDetailResponse toDetail(Booking booking, String bearerToken) {
        AdminUserInfoResponse customerInfo = userServiceClient.getUserInfo(booking.getCustomerId(), bearerToken);
        AdminUserInfoResponse driverInfo = userServiceClient.getUserInfo(booking.getAssignedDriverId(), bearerToken);
        return AdminBookingDetailResponse.fromEntity(booking, customerInfo, driverInfo);
    }

    private void publishRideCreated(Booking booking) {
        RideCreatedEvent event = RideCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideCreatedEvent.EVENT_TYPE)
                .rideId(booking.getId().toString())
                .customerId(booking.getCustomerId())
                .customerNote(booking.getCustomerNote())
                .pickupAddress(booking.getPickupLocation())
                .dropoffAddress(booking.getDropoffLocation())
                .pickup(coordinateMap(booking.getPickupLat(), booking.getPickupLng()))
                .dropoff(coordinateMap(booking.getDropoffLat(), booking.getDropoffLng()))
                .vehicleType(booking.getVehicleType() == null ? null : booking.getVehicleType().name())
                .paymentMethod(booking.getPaymentMethod())
                .estimatedFare(booking.getEstimatedFare())
                .promoCode(booking.getPromoCode())
                .matchingAttempt(1)
                .searchRadiusKm(3.0)
                .rematch(true)
                .excludedDriverIds(List.of())
                .timestamp(Instant.now().toString())
                .build();
        bookingEventPublisher.publishRideCreated(event);
    }

    private Map<String, Double> coordinateMap(Double lat, Double lng) {
        Map<String, Double> coordinates = new java.util.HashMap<>();
        coordinates.put("lat", lat);
        coordinates.put("lng", lng);
        return coordinates;
    }

    private void audit(
            Booking booking,
            String adminId,
            String action,
            BookingStatus oldStatus,
            BookingStatus newStatus,
            String reason,
            String metadata) {
        auditRepository.save(AdminBookingAudit.builder()
                .bookingId(booking.getId())
                .adminId(adminId)
                .action(action)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .metadata(metadata)
                .build());
        log.info("Admin booking audit | bookingId={} | adminId={} | action={} | {} -> {}",
                booking.getId(), adminId, action, oldStatus, newStatus);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Clear matching-service Redis state for a ride before admin retry.
     * Without this, {@code hasAssigned(rideId)} in matching-service would return true
     * and skip the new matching attempt entirely.
     */
    private void clearMatchingRedisState(String rideId) {
        try {
            redisTemplate.delete(MATCHING_ASSIGNED_PREFIX + rideId);
            redisTemplate.delete(MATCHING_DRIVER_PREFIX + rideId);
            redisTemplate.delete(MATCHING_LOCK_PREFIX + rideId);
            redisTemplate.delete(MATCHING_REQUEST_PREFIX + rideId);
            redisTemplate.delete(MATCHING_FAILED_PREFIX + rideId);
            redisTemplate.delete(MATCHING_ATTEMPT_PREFIX + rideId);
            redisTemplate.delete(MATCHING_COOLDOWN_PREFIX + rideId);
            redisTemplate.delete(String.format(RIDE_REJECTED_DRIVERS_KEY_PATTERN, rideId));
            redisTemplate.delete(BOOKING_CANCELLED_PREFIX + rideId);
            log.info("Cleared matching Redis state before admin retry | rideId={}", rideId);
        } catch (RuntimeException ex) {
            log.warn("Failed to clear some matching Redis keys | rideId={} | reason={}", rideId, ex.getMessage());
        }
    }
}
