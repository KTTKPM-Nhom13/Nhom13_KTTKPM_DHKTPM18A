package iuh.fit.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {
    @NotBlank
    @Size(max = 150)
    String fullName;

    @NotBlank
    @Email
    @Size(max = 150)
    String email;

    @NotBlank
    @Size(min = 6, max = 100)
    String password;

    @Size(max = 20)
    String phoneNumber;

    @Size(max = 500)
    String avatarUrl;

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
