package iuh.fit.payment_service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    PAYMENT_NOT_FOUND("PAYMENT_001", "Payment transaction not found", 404),
    PAYMENT_GATEWAY_ERROR("PAYMENT_002", "Payment gateway error", 502),
    PAYMENT_GATEWAY_TIMEOUT("PAYMENT_003", "Payment gateway timeout", 504),
    IDEMPOTENCY_CONFLICT("PAYMENT_004", "Idempotency key already used", 409),
    INVALID_PAYMENT_STATE("PAYMENT_005", "Invalid payment state transition", 400),
    PAYMENT_ALREADY_COMPLETED("PAYMENT_006", "Payment already completed", 400),
    PAYMENT_ALREADY_FAILED("PAYMENT_007", "Payment already failed", 400),
    VALIDATION_ERROR("PAYMENT_008", "Validation error", 400),
    INSUFFICIENT_AMOUNT("PAYMENT_009", "Insufficient amount", 400),
    PAYMENT_RETRY_EXHAUSTED("PAYMENT_010", "Payment retry exhausted", 500),
    GATEWAY_DECLINED("PAYMENT_011", "Payment declined by gateway", 400),
    UNCATEGORIZED_ERROR("PAYMENT_999", "Uncategorized error", 500);

    private final String code;
    private final String message;
    private final int httpStatus;
}
