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
    VALIDATION_ERROR(400, "Validation failed", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
