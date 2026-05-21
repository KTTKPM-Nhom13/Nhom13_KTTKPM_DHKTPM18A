package iuh.fit.driverservice.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverRevenueStatsResponse {
    String driverId;
    String driverName;
    BigDecimal totalGross;
    BigDecimal totalDriver;
    BigDecimal totalPlatform;
    Long totalRides;
}
