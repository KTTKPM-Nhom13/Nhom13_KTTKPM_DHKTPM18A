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
public class DriverStatusCheckResponse {
    String externalUserId;
    String availabilityStatus;
    boolean online;
    boolean offline;
    boolean activeForBooking;
    String verificationStatus;
    String currentRideId;
    String currentRideStatus;
    BigDecimal currentLatitude;
    BigDecimal currentLongitude;
    LocalDateTime lastOnlineAt;
}
