package iuh.fit.driverservice.repository;

import iuh.fit.driverservice.entity.DriverEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DriverEarningRepository extends JpaRepository<DriverEarning, UUID> {
    boolean existsByPaymentEventId(String paymentEventId);
    boolean existsByRideIdAndDriverId(String rideId, String driverId);

    @Query("SELECT SUM(e.grossAmount) as totalGross, SUM(e.driverAmount) as totalDriver, " +
            "SUM(e.platformAmount) as totalPlatform, COUNT(e) as totalRides " +
            "FROM DriverEarning e WHERE e.driverId = :driverId AND e.createdAt BETWEEN :start AND :end")
    Map<String, Object> getEarningStatsByDriverId(@Param("driverId") String driverId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    @Query("SELECT e.driverId as driverId, SUM(e.grossAmount) as totalGross, " +
            "SUM(e.driverAmount) as totalDriver, SUM(e.platformAmount) as totalPlatform, COUNT(e) as totalRides " +
            "FROM DriverEarning e WHERE e.createdAt BETWEEN :start AND :end GROUP BY e.driverId")
    List<Map<String, Object>> getAllDriversEarningStats(@Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);
}
