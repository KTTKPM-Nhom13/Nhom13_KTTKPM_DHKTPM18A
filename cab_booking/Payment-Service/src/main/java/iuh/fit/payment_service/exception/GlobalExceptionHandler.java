package iuh.fit.payment_service.exception;

import iuh.fit.common.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = PaymentException.class)
    public ResponseEntity<ApiResponse<?>> handlingPaymentException(PaymentException exception) {
        log.error("PaymentException: code={}, message={}", exception.getErrorCode().getCode(), exception.getMessage());
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<?> response = ApiResponse.builder()
                .code(errorCode.getHttpStatus())
                .message(errorCode.getMessage())
                .errorMessage(errorCode.getCode())
                .result(exception.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(value = PaymentGatewayException.class)
    public ResponseEntity<ApiResponse<?>> handlingGatewayException(PaymentGatewayException exception) {
        log.error("PaymentGatewayException: gatewayCode={}, message={}", exception.getGatewayCode(), exception.getMessage(), exception);
        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.BAD_GATEWAY.value())
                .message("Payment gateway error: " + exception.getGatewayMessage())
                .errorMessage(ErrorCode.PAYMENT_GATEWAY_ERROR.getCode())
                .result(exception.getGatewayCode())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(value = IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<?>> handlingIdempotencyConflict(IdempotencyConflictException exception) {
        log.warn("IdempotencyConflict: key={}, existingTxn={}", exception.getIdempotencyKey(), exception.getExistingTransactionId());
        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.CONFLICT.value())
                .message(exception.getMessage())
                .errorMessage(ErrorCode.IDEMPOTENCY_CONFLICT.getCode())
                .result(exception.getExistingTransactionId())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handlingValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation errors: {}", errors);
        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .errorMessage(ErrorCode.VALIDATION_ERROR.getCode())
                .result(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<?>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled exception", exception);
        ApiResponse<?> response = ApiResponse.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(ErrorCode.UNCATEGORIZED_ERROR.getMessage())
                .errorMessage(ErrorCode.UNCATEGORIZED_ERROR.getCode())
                .result(exception.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
