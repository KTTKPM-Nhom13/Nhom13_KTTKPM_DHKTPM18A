package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpsertDriverProfileRequest {
    @NotBlank
    @Size(max = 150)
    String fullName;

    @Email
    @Size(max = 150)
    String email;

    @Size(max = 20)
    String phoneNumber;

    @Size(max = 500)
    String avatarUrl;

    @NotBlank
    @Size(max = 100)
    String licenseNumber;

    @NotBlank
    @Size(max = 50)
    String vehicleType;

    @NotBlank
    @Size(max = 50)
    String vehiclePlate;

    @NotBlank
    @Size(max = 120)
    String vehicleModel;

    @NotBlank
    @Size(max = 50)
    String vehicleColor;

    @Size(max = 255)
    String serviceArea;
}
