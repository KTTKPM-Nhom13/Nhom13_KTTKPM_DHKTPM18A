package iuh.fit.driverservice.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverEarningsSummaryResponse {
    String externalUserId;
    String availabilityStatus;
    Integer totalCompletedRides;
    BigDecimal averageRating;
    BigDecimal totalEarnings;
    BigDecimal totalGrossAmount;
    BigDecimal totalDriverAmount;
    boolean currentRideActive;
    LocalDateTime lastOnlineAt;
}
