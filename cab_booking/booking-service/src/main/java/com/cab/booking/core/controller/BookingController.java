package com.cab.booking.core.controller;

import iuh.fit.common.dto.response.ApiResponse;
import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;
import com.cab.booking.core.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * BookingController — Quản lý các API endpoints của chuyến đi.
 *
 * <p>
 * Demo endpoints:
 * - GET /api/v1/bookings/demo/success — Trả về response thành công
 * - GET /api/v1/bookings/demo/not-found — Ném BookingException (404)
 * - GET /api/v1/bookings/demo/conflict — Ném BookingException (409)
 * - POST /api/v1/bookings — Tạo chuyến đi mới
 * - POST /api/v1/bookings/{id}/start — Bắt đầu chuyến đi
 * - POST /api/v1/bookings/{id}/complete — Hoàn thành chuyến đi
 */
@Tag(name = "Booking API", description = "Quản lý vòng đời chuyến đi (Dành cho Khách hàng, Tài xế và Hệ thống)")
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ============================================================
    // HEALTH CHECK — Không yêu cầu xác thực (public)
    // ============================================================

    /**
     * Ping endpoint — kiểm tra nhanh service còn sống không.
     * Không cần Bearer token.
     * GET /api/v1/bookings/ping
     */
    @Operation(summary = "Ping", description = "Kiểm tra nhanh booking-service có đang hoạt động không")
    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success("booking-service is running", Map.of(
                "service", "booking-service",
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // ============================================================
    // DEMO ENDPOINTS — Để test standardized response & exception handling
    // ============================================================

    @GetMapping("/demo/success")
    public ApiResponse<String> demoSuccess() {
        return ApiResponse.success("Lấy danh sách chuyến đi thành công", "Dữ liệu demo thành công");
    }

    @GetMapping("/demo/not-found")
    public ApiResponse<Void> demoNotFound() {
        throw new BookingException(ErrorCode.BOOKING_NOT_FOUND);
    }

    @GetMapping("/demo/conflict")
    public ApiResponse<Void> demoConflict() {
        throw new BookingException(ErrorCode.BOOKING_ALREADY_COMPLETED,
                "Chuyến đi đã hoàn thành trước đó. Không thể thực hiện hủy.");
    }

    // ============================================================
    // PRODUCTION ENDPOINTS
    // ============================================================

    @PostMapping
    public ApiResponse<BookingResponse> createRide(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingRequest request) {
        
        // subject = user UUID được set bởi auth-service
        String customerId = (jwt != null) ? jwt.getSubject() : "f7ee4a2e-236c-4cc7-90db-efc923397cd8"; // Default demo ID
        
        return ApiResponse.success("Tạo chuyến đi thành công",
                bookingService.createRide(customerId, request));
    }

    // ============================================================
    // API KHÁCH HÀNG (CUSTOMER)
    // ============================================================

    @GetMapping("/customer/{customerId}")
    public ApiResponse<org.springframework.data.domain.Page<BookingResponse>> getCustomerHistory(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy lịch sử chuyến đi thành công",
                bookingService.getCustomerHistory(customerId, page, size));
    }

    @GetMapping("/customer/{customerId}/active")
    public ApiResponse<BookingResponse> getActiveBooking(@PathVariable String customerId) {
        return ApiResponse.success("Lấy cuốc xe hiện tại thành công",
                bookingService.getActiveBookingByCustomer(customerId));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Void> reviewRide(@PathVariable UUID id, @RequestBody Object reviewData) {
        // TODO: Chuyển dữ liệu review sang Review Service
        return ApiResponse.success("Đánh giá chuyến đi thành công", null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancelRide(@PathVariable UUID id,
            @RequestParam(defaultValue = "Khách hàng yêu cầu hủy") String reason) {
        return ApiResponse.success("Hủy chuyến đi thành công",
                bookingService.cancelRide(id, reason));
    }

    // ============================================================
    // API TÀI XẾ (DRIVER)
    // Các thao tác của tài xế (nhận chuyến, đến điểm đón, bắt đầu, hoàn thành)
    // bắt buộc phải gọi qua Driver Service. Booking Service chỉ lắng nghe qua Kafka.
    // ============================================================

    @GetMapping("/driver/{driverId}")
    public ApiResponse<org.springframework.data.domain.Page<BookingResponse>> getDriverHistory(
            @PathVariable String driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy lịch sử chuyến đi tài xế thành công",
                bookingService.getDriverHistory(driverId, page, size));
    }

    @GetMapping("/nearby")
    public ApiResponse<java.util.List<BookingResponse>> getNearbyBookings(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        return ApiResponse.success("Danh sách cuốc xe xung quanh",
                bookingService.getNearbyMatchingBookings(lat, lng, radius));
    }

}
