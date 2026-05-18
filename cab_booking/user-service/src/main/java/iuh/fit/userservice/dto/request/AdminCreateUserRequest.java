package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminCreateUserRequest {
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
}
