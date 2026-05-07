package iuh.fit.driverservice.repository;

import iuh.fit.driverservice.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    Optional<DriverProfile> findByExternalUserId(String externalUserId);
}
