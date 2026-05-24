package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Request DTO for the driver heartbeat location endpoint.
 * {@code PATCH /api/drivers/me/location}
 *
 * <p>Used by ONLINE drivers (without active ride) to update their availability location.
 * driverId is extracted from JWT — never from the request body.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateDriverLocationRequest {

    @NotNull(message = "lat is required")
    @DecimalMin(value = "-90.000000")
    @DecimalMax(value = "90.000000")
    BigDecimal lat;

    @NotNull(message = "lng is required")
    @DecimalMin(value = "-180.000000")
    @DecimalMax(value = "180.000000")
    BigDecimal lng;
}
