package iuh.fit.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank
    @Size(min = 6, max = 100)
    String currentPassword;

    @NotBlank
    @Size(min = 6, max = 100)
    String newPassword;
}
