package com.cab.booking.core.controller;

import com.cab.booking.common.ApiResponse;
import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;
import com.cab.booking.core.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** Tạo chuyến đi mới — FE gọi */
    @PostMapping
    public ApiResponse<BookingResponse> createRide(@Valid @RequestBody BookingRequest request) {
        String mockCustomerId = "cus-mock-123"; // TODO: thay bằng JWT customerId khi Auth Service hoàn thiện
        return ApiResponse.success(bookingService.createRide(mockCustomerId, request));
    }

    /** Driver App: bắt đầu chuyến đi — ASSIGNED → IN_PROGRESS */
    @PostMapping("/{id}/start")
    public ApiResponse<BookingResponse> startRide(@PathVariable UUID id) {
        return ApiResponse.success(bookingService.startRide(id));
    }

    /** Driver App: hoàn thành chuyến đi — IN_PROGRESS → COMPLETED */
    @PostMapping("/{id}/complete")
    public ApiResponse<BookingResponse> completeRide(@PathVariable UUID id) {
        return ApiResponse.success(bookingService.completeRide(id));
    }
}
