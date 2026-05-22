package com.cab.booking.core.service;

import com.cab.booking.core.dto.request.AdminCancelBookingRequest;
import com.cab.booking.core.dto.request.AdminUpdateBookingStatusRequest;
import com.cab.booking.core.dto.response.AdminBookingDetailResponse;
import com.cab.booking.core.dto.response.AdminBookingSummaryResponse;
import com.cab.booking.core.dto.response.AdminBookingTimelineResponse;
import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.enums.VehicleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminBookingService {
    Page<AdminBookingSummaryResponse> searchBookings(
            BookingStatus status,
            String customerId,
            String driverId,
            String paymentMethod,
            VehicleType vehicleType,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Pageable pageable);

    AdminBookingDetailResponse getBookingDetail(UUID bookingId, String bearerToken);

    List<AdminBookingTimelineResponse> getTimeline(UUID bookingId);

    AdminBookingDetailResponse cancelBooking(
            UUID bookingId,
            AdminCancelBookingRequest request,
            String adminId,
            String bearerToken);

    AdminBookingDetailResponse retryMatching(UUID bookingId, String adminId, String bearerToken);

    AdminBookingDetailResponse updateStatus(
            UUID bookingId,
            AdminUpdateBookingStatusRequest request,
            String adminId,
            String bearerToken);
}
