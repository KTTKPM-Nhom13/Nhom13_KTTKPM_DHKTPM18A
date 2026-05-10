package com.cab.booking.common;

import org.springframework.http.HttpStatus;

/**
 * ErrorCode — Enum định nghĩa toàn bộ mã lỗi nghiệp vụ của hệ thống.
 *
 * <p>Tại sao dùng enum thay vì String constant?
 * - Compile-time safety: không thể nhập sai mã lỗi.
 * - Mỗi ErrorCode gắn với HttpStatus tương ứng — tránh mapping sai ở handler.
 * - Có thể mở rộng: thêm description, httpStatus, isRetryable... sau này.
 * - Dùng Pattern Matching khi xử lý trong GlobalExceptionHandler.
 *
 * <p>Quy ước naming: UPPERCASE_WITH_UNDERSCORE (VD: BOOKING_NOT_FOUND)
 * → JSON output: error_code = "BOOKING_NOT_FOUND".
 */
public enum ErrorCode {

    // ===== 400 Bad Request — Lỗi dữ liệu đầu vào =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "Thiếu trường bắt buộc"),
    INVALID_BOOKING_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái booking không hợp lệ cho thao tác này"),

    // ===== 404 Not Found — Tài nguyên không tồn tại =====
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy chuyến đi"),
    DRIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài xế"),
    PASSENGER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hành khách"),
    VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phương tiện"),

    // ===== 409 Conflict — Xung đột nghiệp vụ =====
    BOOKING_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Chuyến đi đã bị hủy trước đó"),
    BOOKING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Chuyến đi đã hoàn thành"),
    DRIVER_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Tài xế đã được phân công cho chuyến đi khác"),
    RIDE_IN_PROGRESS(HttpStatus.CONFLICT, "Chuyến đi đang trong quá trình thực hiện"),

    // ===== 422 Unprocessable Entity — Nghiệp vụ vi phạm logic =====
    DRIVER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Tài xế hiện không khả dụng"),
    NO_DRIVERS_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Không có tài xế nào khả dụng trong khu vực"),
    PASSENGER_HAS_ACTIVE_RIDE(HttpStatus.UNPROCESSABLE_ENTITY, "Hành khách đang có chuyến đi đang hoạt động"),

    // ===== 503 Service Unavailable — Lỗi phụ thuộc =====
    DRIVER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Driver Service tạm thời không khả dụng"),
    PRICING_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Pricing Service tạm thời không khả dụng"),

    // ===== 500 Internal Server Error — Lỗi hệ thống =====
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống nội bộ");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /** Trả về HTTP status code tương ứng với mã lỗi. */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /** Trả về message mặc định khi không override message. */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
