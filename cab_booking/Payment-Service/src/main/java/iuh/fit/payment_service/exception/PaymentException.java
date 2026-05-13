package iuh.fit.payment_service.exception;

import lombok.Getter;

@Getter
public class PaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PaymentException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
