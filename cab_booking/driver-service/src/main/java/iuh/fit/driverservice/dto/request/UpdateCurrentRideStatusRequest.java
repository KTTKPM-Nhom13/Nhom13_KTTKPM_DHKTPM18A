package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCurrentRideStatusRequest {

    @NotBlank
    @Size(max = 30)
    String rideStatus;

    BigDecimal currentLatitude;

    BigDecimal currentLongitude;
}
