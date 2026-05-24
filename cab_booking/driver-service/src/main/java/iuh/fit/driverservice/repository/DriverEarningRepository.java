package iuh.fit.driverservice.repository;

import iuh.fit.driverservice.entity.DriverEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface DriverEarningRepository extends JpaRepository<DriverEarning, UUID> {
    boolean existsByPaymentEventId(String paymentEventId);
    boolean existsByRideIdAndDriverId(String rideId, String driverId);

    @Query("SELECT COALESCE(SUM(e.grossAmount), 0) FROM DriverEarning e WHERE e.driverId = :driverId")
    BigDecimal sumGrossAmountByDriverId(@Param("driverId") String driverId);

    @Query("SELECT COALESCE(SUM(e.driverAmount), 0) FROM DriverEarning e WHERE e.driverId = :driverId")
    BigDecimal sumDriverAmountByDriverId(@Param("driverId") String driverId);
}
