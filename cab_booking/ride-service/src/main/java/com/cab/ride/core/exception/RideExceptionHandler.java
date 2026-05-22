package com.cab.ride.core.exception;

import iuh.fit.common.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

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
}