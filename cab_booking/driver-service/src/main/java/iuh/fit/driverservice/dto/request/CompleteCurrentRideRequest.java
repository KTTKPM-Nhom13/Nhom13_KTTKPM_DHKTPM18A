package iuh.fit.driverservice.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteCurrentRideRequest {
    BigDecimal fareAmount;
    BigDecimal distanceKm;
}
