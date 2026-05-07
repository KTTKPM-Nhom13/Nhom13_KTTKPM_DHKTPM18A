package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateDriverRideProgressRequest {
    @NotBlank
    @Size(max = 30)
    String rideStatus;

    @DecimalMin(value = "-90.000000")
    @DecimalMax(value = "90.000000")
    BigDecimal currentLatitude;

    @DecimalMin(value = "-180.000000")
    @DecimalMax(value = "180.000000")
    BigDecimal currentLongitude;
}
