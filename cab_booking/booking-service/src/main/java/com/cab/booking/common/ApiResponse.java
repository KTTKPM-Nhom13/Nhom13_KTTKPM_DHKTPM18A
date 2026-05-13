package com.cab.booking.common;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

/**
 * ApiResponse<T> — Wrapper chuẩn hóa mọi response trả về từ API.
 *
 * <p>Tại sao dùng Java 21 Record thay vì class thường / Lombok?
 * - Record tự động sinh: constructor, equals(), hashCode(), toString(), getter methods.
 * - Immutable by default — đảm bảo response không bị thay đổi sau khi trả về.
 * - Compiler guarantee: không thể khai báo field mutable (không có setter).
 * - Giảm boilerplate, tăng type safety, record là value object thuần.
 *
 * <p>Spring Boot 4 / Jackson 3: JsonNaming dùng SCREAMING_SNAKE_CASE
 * để đảm bảo JSON output nhất quán: success, error_code, booking_id, v.v.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiResponse<T>(
        /** Trạng thái thành công hay thất bại của request. */
        boolean success,

        /** Thông điệp mô tả ngắn gọn kết quả (dùng cho cả success lẫn error). */
        String message,

        /** Dữ liệu trả về — generic type, có thể là object, list, hoặc null khi error. */
        T data,

        /** Mã lỗi theo chuẩn hệ thống (VD: BOOKING_NOT_FOUND, INVALID_REQUEST).
         *  Chỉ điền khi success = false. */
        String errorCode,

        /** Thời điểm server tạo response (UTC). Dùng để debug và trace request. */
        LocalDateTime timestamp
) {

    // ============================================================
    // FACTORY METHODS — Tạo nhanh response không cần new ApiResponse(...)
    // ============================================================

    /**
     * Tạo response thành công với dữ liệu.
     * timestamp sẽ được gán tự động bằng LocalDateTime.now().
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Thành công", data, null, LocalDateTime.now());
    }

    /**
     * Tạo response thành công với custom message và dữ liệu.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    /**
     * Tạo response thành công chỉ có message (không có data).
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, LocalDateTime.now());
    }

    /**
     * Tạo response lỗi với errorCode (String) — dùng khi cần custom mã lỗi
     * không nằm trong enum ErrorCode.
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode, LocalDateTime.now());
    }

    /**
     * Tạo response lỗi chỉ với message — mã lỗi mặc định là "INTERNAL_ERROR".
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, "INTERNAL_ERROR", LocalDateTime.now());
    }

    /**
     * Tạo response lỗi khi server gặp exception không xác định.
     * Message gốc được ẩn để không leak thông tin nhạy cảm ra client.
     */
    public static <T> ApiResponse<T> errorInternal() {
        return new ApiResponse<>(false, "Lỗi hệ thống. Vui lòng thử lại sau.", null, "INTERNAL_ERROR", LocalDateTime.now());
    }
}
