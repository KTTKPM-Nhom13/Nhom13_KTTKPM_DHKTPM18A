package iuh.fit.pricing_service.repository;

import iuh.fit.pricing_service.model.FareEstimate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FareEstimateRepository extends MongoRepository<FareEstimate, String> {

    Optional<FareEstimate> findByRideId(String rideId);

    List<FareEstimate> findByStatus(String status);

    List<FareEstimate> findByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);

    List<FareEstimate> findByVehicleType(String vehicleType);

    List<FareEstimate> findByPickupZone(String pickupZone);

    List<FareEstimate> findByDropoffZone(String dropoffZone);

    List<FareEstimate> findByVehicleTypeAndStatus(String vehicleType, String status);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
