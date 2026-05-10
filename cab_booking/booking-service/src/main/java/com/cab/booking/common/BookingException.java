package com.cab.booking.common;

import org.springframework.http.HttpStatus;

/**
 * BookingException — Exception nghiệp vụ tùy chỉnh cho toàn bộ hệ thống.
 *
 * <p>Tại sao kế thừa RuntimeException thay vì Exception?
 * - Không cần khai báo throws — giảm boilerplate ở method signature.
 * - Tự động unwrap trong Spring Transaction (rollback mặc định).
 *
 * <p>Tại sao dùng ErrorCode enum thay vì truyền String status/message?
 * - ErrorCode gắn HttpStatus cố định — tránh trả về 404 khi nên trả 409.
 * - Team biết chính xác mã lỗi nghiệp vụ, không phải đoán.
 * - Dễ trace log, monitoring, alerting theo error code cụ thể.
 *
 * <p>Spring Boot 4 / Jakarta EE 11: Dùng Pattern Matching instanceof
 * để kiểm tra type không cần cast trong GlobalExceptionHandler.
 *
 * <p>Ví dụ sử dụng:
 * <pre>
 *     throw new BookingException(ErrorCode.BOOKING_NOT_FOUND);
 *     throw new BookingException(ErrorCode.INVALID_REQUEST, "Email không hợp lệ");
 * </pre>
 */
public class BookingException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Tạo exception với ErrorCode — dùng message mặc định của enum.
     */
    public BookingException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * Tạo exception với ErrorCode và custom message ghi đè.
     * Dùng khi cần trả về message cụ thể hơn cho từng ngữ cảnh.
     */
    public BookingException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    /**
     * Tạo exception với ErrorCode, custom message, và nguyên nhân gốc (cause).
     * Dùng khi wrap một exception khác (VD: gọi service bên ngoài).
     */
    public BookingException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
    }

    /** Trả về ErrorCode để handler map sang HTTP status chính xác. */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Trả về HTTP status tương ứng — shortcut cho handler. */
    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /**
     * Trả về errorCode dạng String — dùng khi ghi log hoặc build response.
     * Format: BOOKING_NOT_FOUND (tên enum).
     */
    public String getErrorCodeName() {
        return errorCode.name();
    }
}
