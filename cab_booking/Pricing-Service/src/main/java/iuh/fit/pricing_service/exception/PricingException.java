package iuh.fit.pricing_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PricingException extends RuntimeException {

    private final String errorCode;

    public PricingException(String message) {
        super(message);
        this.errorCode = "PRICING_ERROR";
    }

    public PricingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PricingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PRICING_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
