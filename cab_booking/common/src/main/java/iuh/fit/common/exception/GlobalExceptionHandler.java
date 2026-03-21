package iuh.fit.common.exception;

import iuh.fit.common.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<?>> handlingRuntimeException(Exception exception) {
    log.error("Unhandled exception", exception);

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                .build();

        return ResponseEntity
                .status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode())
                .body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<?>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn("Application exception: {}", errorCode.name(), exception);

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(exception.getErrorCode().getStatusCode())
                .body(apiResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<?>> handlingValidation(MethodArgumentNotValidException exception) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        String errorMessage = null;

        var fieldError = exception.getFieldError();
        if (fieldError != null) {
            String enumKey = fieldError.getDefaultMessage();
            errorMessage = fieldError.getField() + ": " + fieldError.getDefaultMessage();
            try {
                if (enumKey != null) {
                    errorCode = ErrorCode.valueOf(enumKey);
                }
            } catch (IllegalArgumentException e) {
                // Keep default validation error when defaultMessage is not an enum key.
            }
        }

        log.debug("Validation failed: {}", exception.getMessage());

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .errorMessage(errorMessage)
                .build();

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

}
