package com.cab.ride.core.exception;

import iuh.fit.common.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for ride-service.
 * Ensures ALL errors return the standard ApiResponse format:
 * { code, message, errorMessage, timestamp }
 */
@RestControllerAdvice
public class RideExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse<Void> handleResponseStatusException(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        String errorMessage;
        if (ex.getStatusCode() instanceof HttpStatus httpStatus) {
            errorMessage = httpStatus.getReasonPhrase();
        } else {
            errorMessage = ex.getStatusCode().toString();
        }

        return ApiResponse.<Void>builder()
                .code(code)
                .message(message)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ApiResponse.<Void>builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(message.isEmpty() ? "Validation failed" : message)
                .errorMessage("VALIDATION_ERROR")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGenericException(Exception ex) {
        return ApiResponse.<Void>builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Internal server error")
                .errorMessage(ex.getClass().getSimpleName())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
