package iuh.fit.pricing_service.repository;

import iuh.fit.pricing_service.model.PricingConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingConfigRepository extends MongoRepository<PricingConfig, String> {

    Optional<PricingConfig> findByVehicleType(String vehicleType);

    List<PricingConfig> findByActive(Boolean active);

    List<PricingConfig> findByVehicleTypeAndActive(String vehicleType, Boolean active);
}
