package iuh.fit.driverservice.repository;

import iuh.fit.driverservice.entity.DriverEarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverEarningRepository extends JpaRepository<DriverEarning, UUID> {
    boolean existsByPaymentEventId(String paymentEventId);
    boolean existsByRideIdAndDriverId(String rideId, String driverId);
}
