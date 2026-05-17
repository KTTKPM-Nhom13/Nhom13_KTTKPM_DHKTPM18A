package iuh.fit.driverservice.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_profiles")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DriverProfile extends BaseEntity {

    @Column(name = "external_user_id", nullable = false, unique = true, length = 100)
    String externalUserId;

    @Column(name = "full_name", length = 150)
    String fullName;

    @Column(length = 150)
    String email;

    @Column(name = "phone_number", length = 20)
    String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(name = "license_number", length = 100)
    String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 50)
    VehicleType vehicleType;

    @Column(name = "vehicle_plate", length = 50)
    String vehiclePlate;

    @Column(name = "vehicle_model", length = 120)
    String vehicleModel;

    @Column(name = "vehicle_color", length = 50)
    String vehicleColor;

    @Column(name = "service_area", length = 255)
    String serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    DriverAvailabilityStatus availabilityStatus = DriverAvailabilityStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    DriverVerificationStatus verificationStatus = DriverVerificationStatus.PENDING;

    @Column(name = "current_latitude", precision = 10, scale = 6)
    BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 6)
    BigDecimal currentLongitude;

    @Column(name = "last_online_at")
    LocalDateTime lastOnlineAt;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @Column(name = "total_completed_rides", nullable = false)
    Integer totalCompletedRides = 0;

    @Column(name = "average_rating", precision = 3, scale = 2)
    BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    Integer totalReviews = 0;

    @Column(name = "total_earnings", precision = 18, scale = 2)
    BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "current_ride_id", length = 100)
    String currentRideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_ride_status", length = 30)
    DriverRideStatus currentRideStatus;

    @Column(name = "current_ride_pickup", length = 255)
    String currentRidePickup;

    @Column(name = "current_ride_destination", length = 255)
    String currentRideDestination;

    @Column(name = "current_ride_requested_at")
    LocalDateTime currentRideRequestedAt;
}
