package com.cab.booking.common.exception;

import com.cab.booking.common.ApiResponse;
import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Xử lý lỗi tập trung cho toàn bộ API.
 *
 * <p>Tại sao dùng @RestControllerAdvice thay vì @ControllerAdvice?
 * - @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * - Tự động serialize kết quả sang JSON mà không cần thêm annotation ở mỗi handler.
 *
 * <p>Tại sao trả về ResponseEntity<ApiResponse<Void>> thay vì void?
 * - Kiểm soát HTTP status code chính xác theo loại lỗi.
 * - Giữ format nhất quán: {success, message, data, error_code, timestamp}.
 *
 * <p>Spring Boot 4: Không cần @EnableWebMvc (đã mặc định bật trong Spring Boot 4).
 * Handler tự động nhận diện các exception type từ classpath.
 *
 * <p>Thứ tự xử lý: Handler cụ thể nhất → Handler chung nhất.
 * BookingException phải đứng TRƯỚC Exception để tránh bị catch bởi handler tổng.
 *
 * <p>Jakarta EE 11: Dùng MethodArgumentNotValidException (jakarta.validation) thay
 * vì BindException (javax.validation) của các phiên bản cũ.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============================================================
    // BOOKING EXCEPTION — Lỗi nghiệp vụ tùy chỉnh (mã lỗi rõ ràng)
    // ============================================================

    /**
     * Xử lý BookingException — lỗi nghiệp vụ có mã xác định.
     *
     * <p>Spring Boot 4: Pattern Matching instanceof không cần cast.
     * <pre>
     *     if (ex instanceof BookingException be) {
     *         ErrorCode code = be.getErrorCode();  // không cần ((BookingException) ex)
     *     }
     * </pre>
     *
     * <p>Luôn log error code + message để tracing, KHÔNG leak stack trace ra client.
     */
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookingException(BookingException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        // Log đầy đủ cho team debug — không trả message gốc ra client (tránh leak)
        log.error("[BookingException] errorCode={}, message={}", errorCode.name(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), errorCode.name());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    // ============================================================
    // VALIDATION EXCEPTION — Lỗi validate dữ liệu đầu vào (Jakarta EE)
    // ============================================================

    /**
     * Xử lý MethodArgumentNotValidException — lỗi khi @Valid thất bại trên request body.
     *
     * <p>Gom tất cả field lỗi thành một message duy nhất, dễ đọc cho client:
     * "pickup_location: Không được để trống; passenger_phone: Số điện thoại không hợp lệ"
     *
     * <p>Tại sao dùng Stream.Collectors.joining()?
     * - Nối các lỗi bằng "; " thay vì "\n" — phù hợp hiển thị trên UI mobile.
     * - Message ngắn gọn, không spam nhiều dòng cho client.
     *
     * <p>Jakarta EE 11: MethodArgumentNotValidException dùng jakarta.validation,
     * hỗ trợ các annotation như @NotBlank, @NotNull, @Size, @Email, @Pattern...
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[Validation] fields={}, message={}",
                ex.getBindingResult().getFieldErrors().size(), message);

        ApiResponse<Void> response = ApiResponse.error(
                message.isBlank() ? "Dữ liệu đầu vào không hợp lệ" : message,
                ErrorCode.VALIDATION_FAILED.name()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Xử lý HandlerMethodValidationException — lỗi validate parameter trên method.
     *
     * <p>Spring 6.1 / Spring Boot 4: Đây là exception mới thay thế việc validate
     * parameter-level (VD: @Valid @RequestParam, @PathVariable) trong các phiên bản cũ.
     *
     * <p>Khác với MethodArgumentNotValidException: xử lý lỗi ở cấp parameter/header
     * chứ không phải request body.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerValidationException(
            HandlerMethodValidationException ex) {

        String message = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[HandlerValidation] errors={}, message={}",
                ex.getAllValidationResults().size(), message);

        ApiResponse<Void> response = ApiResponse.error(
                message.isBlank() ? "Tham số request không hợp lệ" : message,
                ErrorCode.VALIDATION_FAILED.name()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // ============================================================
    // FALLBACK — Bắt toàn bộ exception không mong muốn
    // ============================================================

    /**
     * Xử lý Exception chung — catch tất cả lỗi không được handler cụ thể bắt.
     *
     * <p>QUAN TRỌNG: Handler này phải đứng CUỐI CÙNG vì @ExceptionHandler
     * match theo thứ tự từ trên xuống. Exception cụ thể phải được bắt trước.
     *
     * <p>KHÔNG trả message gốc (ex.getMessage()) ra client vì:
     * - Có thể chứa thông tin nhạy cảm (SQL, stack trace, config path).
     * - Lộ message gốc cho attacker → dễ khai thác lỗ hổng.
     *
     * <p>Luôn log đầy đủ (message + stack trace) để team debug từ server log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Log đầy đủ — không leak ra client
        log.error("[UnhandledException] type={}, message={}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.errorInternal());
    }
}
