package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteDriverRideRequest {
    @NotNull
    @DecimalMin(value = "0.0")
    BigDecimal fareAmount;

    @DecimalMin(value = "0.0")
    BigDecimal distanceKm;

    LocalDateTime completedAt;
}
