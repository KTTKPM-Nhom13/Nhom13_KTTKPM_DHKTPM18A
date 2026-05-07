package iuh.fit.auth_service.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VerifyRegisterOtpResponse {
    String email;
    boolean verified;
    boolean canRegister;
    LocalDateTime verifiedAt;
}
