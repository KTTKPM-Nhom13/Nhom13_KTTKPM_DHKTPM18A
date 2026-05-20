package iuh.fit.driverservice.repository;

import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    Optional<DriverProfile> findByExternalUserId(String externalUserId);
    Optional<DriverProfile> findByCurrentRideId(String currentRideId);
    List<DriverProfile> findByCurrentRideStatusAndCurrentRideRequestedAtBefore(
            DriverRideStatus currentRideStatus,
            LocalDateTime cutoff);
    List<DriverProfile> findAllByOrderByCreatedAtDesc();
}
