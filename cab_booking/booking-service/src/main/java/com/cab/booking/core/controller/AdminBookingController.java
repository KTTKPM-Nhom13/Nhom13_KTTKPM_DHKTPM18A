package com.cab.booking.core.controller;

import com.cab.booking.core.dto.request.AdminCancelBookingRequest;
import com.cab.booking.core.dto.request.AdminUpdateBookingStatusRequest;
import com.cab.booking.core.dto.response.AdminBookingDetailResponse;
import com.cab.booking.core.dto.response.AdminBookingSummaryResponse;
import com.cab.booking.core.dto.response.AdminBookingTimelineResponse;
import com.cab.booking.core.enums.BookingStatus;
import com.cab.booking.core.enums.VehicleType;
import com.cab.booking.core.service.AdminBookingService;
import iuh.fit.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {
    private final AdminBookingService adminBookingService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<AdminBookingSummaryResponse>> searchBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            Pageable pageable) {
        return ApiResponse.success("Fetched admin booking list",
                adminBookingService.searchBookings(
                        status,
                        customerId,
                        driverId,
                        paymentMethod,
                        vehicleType,
                        createdFrom,
                        createdTo,
                        pageable));
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminBookingDetailResponse> getBookingDetail(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Fetched admin booking detail",
                adminBookingService.getBookingDetail(bookingId, bearerToken(jwt)));
    }

    @GetMapping("/{bookingId}/timeline")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminBookingTimelineResponse>> getTimeline(@PathVariable UUID bookingId) {
        return ApiResponse.success("Fetched admin booking timeline",
                adminBookingService.getTimeline(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminBookingDetailResponse> cancelBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminCancelBookingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Cancelled booking by admin",
                adminBookingService.cancelBooking(bookingId, request, adminId(jwt), bearerToken(jwt)));
    }

    @PostMapping("/{bookingId}/retry-matching")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminBookingDetailResponse> retryMatching(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Retried matching by admin",
                adminBookingService.retryMatching(bookingId, adminId(jwt), bearerToken(jwt)));
    }

    @PatchMapping("/{bookingId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminBookingDetailResponse> updateStatus(
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminUpdateBookingStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Updated booking status by admin",
                adminBookingService.updateStatus(bookingId, request, adminId(jwt), bearerToken(jwt)));
    }

    private String adminId(Jwt jwt) {
        return jwt == null ? "unknown-admin" : jwt.getSubject();
    }

    private String bearerToken(Jwt jwt) {
        return jwt == null ? null : jwt.getTokenValue();
    }
}
