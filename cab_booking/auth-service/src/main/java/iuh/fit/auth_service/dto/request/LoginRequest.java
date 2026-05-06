package iuh.fit.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {
    @NotBlank
    @Email
    @Size(max = 150)
    String email;

    @NotBlank
    @Size(max = 100)
    String password;

    @NotBlank
    @Size(max = 120)
    String deviceId;

    @NotBlank
    @Size(max = 30)
    String platform;

    @Size(max = 500)
    String userAgent;

    @Size(max = 50)
    String appVersion;
}
