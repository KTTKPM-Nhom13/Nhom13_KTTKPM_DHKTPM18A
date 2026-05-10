package iuh.fit.payment_service.exception;

import lombok.Getter;

@Getter
public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;
    private final String existingTransactionId;

    public IdempotencyConflictException(String idempotencyKey, String existingTransactionId) {
        super("Idempotency key '" + idempotencyKey + "' already used by transaction '" + existingTransactionId + "'");
        this.idempotencyKey = idempotencyKey;
        this.existingTransactionId = existingTransactionId;
    }
}
