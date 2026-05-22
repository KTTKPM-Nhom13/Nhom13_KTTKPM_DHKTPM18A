package com.cab.booking.common.exception;

import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.common.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ApiResponse<?>> handleBookingException(BookingException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("[BookingException] errorCode={}, message={}", errorCode.name(), ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .code(errorCode.getHttpStatus().value())
                .message(ex.getMessage())
                .errorMessage(errorCode.name())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleCommonAppException(AppException ex) {
        iuh.fit.common.exception.ErrorCode errorCode = ex.getErrorCode();
        log.warn("[AppException] errorCode={}, message={}", errorCode.name(), ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .errorMessage(errorCode.name())
                .build();

        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[Validation] fields={}, message={}",
                ex.getBindingResult().getFieldErrors().size(), message);

        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(hasText(message) ? message : ErrorCode.VALIDATION_FAILED.getDefaultMessage())
                .errorMessage(ErrorCode.VALIDATION_FAILED.name())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleHandlerValidationException(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[HandlerValidation] errors={}, message={}", ex.getAllErrors().size(), message);

        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(hasText(message) ? message : ErrorCode.VALIDATION_FAILED.getDefaultMessage())
                .errorMessage(ErrorCode.VALIDATION_FAILED.name())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("[IllegalArgumentException] message={}", ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.INVALID_REQUEST.getDefaultMessage())
                .errorMessage(ErrorCode.INVALID_REQUEST.name())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("[IllegalStateException] message={}", ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.CONFLICT.value())
                .message(hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.INVALID_BOOKING_STATUS.getDefaultMessage())
                .errorMessage(ErrorCode.INVALID_BOOKING_STATUS.name())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        log.error("[UnhandledException] type={}, message={}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(hasText(ex.getMessage()) ? ex.getMessage() : ErrorCode.INTERNAL_ERROR.getDefaultMessage())
                .errorMessage(ex.getClass().getSimpleName())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
