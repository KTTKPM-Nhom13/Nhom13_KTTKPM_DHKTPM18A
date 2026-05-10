package iuh.fit.common.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Common errors
    UNCATEGORIZED_EXCEPTION(500, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(400, "Invalid key", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(400, "Validation failed", HttpStatus.BAD_REQUEST),
    USER_PROFILE_NOT_FOUND(404, "User profile not found", HttpStatus.NOT_FOUND),
    USER_DEVICE_NOT_FOUND(404, "User device not found", HttpStatus.NOT_FOUND),
    USER_DEVICE_DUPLICATED(409, "User device already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(409, "Email already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(401, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(401, "Refresh token is invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(401, "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_USER_NOT_FOUND(404, "Auth user not found", HttpStatus.NOT_FOUND),
    AUTH_SESSION_NOT_FOUND(404, "Auth session not found", HttpStatus.NOT_FOUND),
    ACCOUNT_DISABLED(403, "Account is disabled", HttpStatus.FORBIDDEN),
    EMAIL_DELIVERY_FAILED(502, "Email delivery failed", HttpStatus.BAD_GATEWAY),
    BOOKING_NOT_FOUND(404, "Booking not found", HttpStatus.NOT_FOUND),
    BOOKING_ALREADY_ACCEPTED(409, "Booking already accepted by another driver", HttpStatus.CONFLICT),
    INVALID_STATE(400, "Invalid state transition", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
