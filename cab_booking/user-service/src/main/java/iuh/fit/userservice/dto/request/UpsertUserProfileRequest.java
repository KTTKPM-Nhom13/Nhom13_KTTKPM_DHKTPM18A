package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpsertUserProfileRequest {

    @NotBlank
    @Size(max = 150)
    String fullName;

    @Email
    @Size(max = 150)
    String email;

    @Pattern(regexp = "^[0-9+\\-() ]{8,20}$", message = "Phone number format is invalid")
    String phoneNumber;

    @Size(max = 500)
    String avatarUrl;

    @Size(max = 20)
    String gender;

    @Past
    LocalDate dateOfBirth;

    @Size(max = 255)
    String defaultPickupNote;
}
