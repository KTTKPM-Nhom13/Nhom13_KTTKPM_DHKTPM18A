package com.cab.booking.core.controller;

import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;
import com.cab.booking.core.service.BookingService;
import iuh.fit.common.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Booking API", description = "Booking lifecycle APIs")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("OK", "booking-service is running");
    }

    @Operation(summary = "Ping", description = "Check whether booking-service is running")
    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success("booking-service is running", Map.of(
                "service", "booking-service",
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/demo/success")
    public ApiResponse<String> demoSuccess() {
        return ApiResponse.success("Fetched demo bookings successfully", "Demo data");
    }

    @GetMapping("/demo/not-found")
    public ApiResponse<Void> demoNotFound() {
        throw new BookingException(ErrorCode.BOOKING_NOT_FOUND);
    }

    @GetMapping("/demo/conflict")
    public ApiResponse<Void> demoConflict() {
        throw new BookingException(ErrorCode.BOOKING_ALREADY_COMPLETED,
                "Booking was already completed. Cannot cancel it.");
    }

    @PostMapping
    public ApiResponse<BookingResponse> createRide(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingRequest request) {
        String customerId = jwt != null ? jwt.getSubject() : "f7ee4a2e-236c-4cc7-90db-efc923397cd8";
        String accessToken = jwt != null ? jwt.getTokenValue() : null;

        return ApiResponse.success("Created booking successfully",
                bookingService.createRide(customerId, accessToken, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> getBookingById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        BookingResponse booking = bookingService.getBookingById(id);
        if (jwt != null && jwt.getSubject() != null) {
            String subject = jwt.getSubject();
            boolean isCustomer = subject.equals(booking.getCustomerId());
            boolean isDriver = subject.equals(booking.getAssignedDriverId());
            if (!isCustomer && !isDriver) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own bookings or bookings assigned to you");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ApiResponse.success("Fetched booking successfully", booking);
    }

    @GetMapping("/customer/{customerId}")
    public ApiResponse<org.springframework.data.domain.Page<BookingResponse>> getCustomerHistory(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        requireSameUser(jwt, customerId);
        return ApiResponse.success("Fetched customer booking history successfully",
                bookingService.getCustomerHistory(customerId, page, size));
    }

    @GetMapping("/customer/{customerId}/active")
    public ApiResponse<BookingResponse> getActiveBooking(
            @PathVariable String customerId,
            @AuthenticationPrincipal Jwt jwt) {
        requireSameUser(jwt, customerId);
        return ApiResponse.success("Fetched active booking successfully",
                bookingService.getActiveBookingByCustomer(customerId));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Void> reviewRide(@PathVariable UUID id, @RequestBody Object reviewData) {
        return ApiResponse.success("Reviewed booking successfully", null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancelRide(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "Customer requested cancellation") String reason,
            @AuthenticationPrincipal Jwt jwt) {
        BookingResponse booking = bookingService.getBookingById(id);
        requireSameUser(jwt, booking.getCustomerId());
        return ApiResponse.success("Cancelled booking successfully",
                bookingService.cancelRide(id, reason));
    }

    @PreAuthorize("hasRole('DRIVER')")
    @GetMapping("/driver/{driverId}")
    public ApiResponse<org.springframework.data.domain.Page<BookingResponse>> getDriverHistory(
            @PathVariable String driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        requireSameUser(jwt, driverId);
        return ApiResponse.success("Fetched driver booking history successfully",
                bookingService.getDriverHistory(driverId, page, size));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ApiResponse<org.springframework.data.domain.Page<BookingResponse>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = requireUserId(jwt);
        return ApiResponse.success("Fetched bookings successfully",
                bookingService.getCustomerHistory(userId, page, size));
    }

    @GetMapping("/nearby")
    public ApiResponse<java.util.List<BookingResponse>> getNearbyBookings(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        return ApiResponse.success("Fetched nearby matching bookings",
                bookingService.getNearbyMatchingBookings(lat, lng, radius));
    }

    private void requireSameUser(Jwt jwt, String customerId) {
        if (jwt == null || jwt.getSubject() == null || !jwt.getSubject().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own bookings");
        }
    }

    private String requireUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return jwt.getSubject();
    }
}
