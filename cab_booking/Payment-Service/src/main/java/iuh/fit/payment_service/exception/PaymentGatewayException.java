package iuh.fit.payment_service.exception;

import lombok.Getter;

@Getter
public class PaymentGatewayException extends RuntimeException {

    private final String gatewayCode;
    private final String gatewayMessage;

    public PaymentGatewayException(String message) {
        super(message);
        this.gatewayCode = "GATEWAY_ERROR";
        this.gatewayMessage = message;
    }

    public PaymentGatewayException(String gatewayCode, String message) {
        super(message);
        this.gatewayCode = gatewayCode;
        this.gatewayMessage = message;
    }

    public PaymentGatewayException(String gatewayCode, String message, Throwable cause) {
        super(message, cause);
        this.gatewayCode = gatewayCode;
        this.gatewayMessage = message;
    }
}
