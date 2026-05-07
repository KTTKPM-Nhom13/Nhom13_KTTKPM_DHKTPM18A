package iuh.fit.driverservice.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverProfileResponse {
    UUID id;
    String externalUserId;
    String fullName;
    String email;
    String phoneNumber;
    String avatarUrl;
    String licenseNumber;
    String vehicleType;
    String vehiclePlate;
    String vehicleModel;
    String vehicleColor;
    String serviceArea;
    String availabilityStatus;
    String verificationStatus;
    BigDecimal currentLatitude;
    BigDecimal currentLongitude;
    LocalDateTime lastOnlineAt;
    LocalDateTime approvedAt;
    Integer totalCompletedRides;
    BigDecimal averageRating;
    BigDecimal totalEarnings;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
