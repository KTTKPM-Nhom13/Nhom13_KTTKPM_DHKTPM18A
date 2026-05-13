package iuh.fit.pricing_service.repository;

import iuh.fit.pricing_service.model.SurgeRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurgeRuleRepository extends MongoRepository<SurgeRule, String> {

    Optional<SurgeRule> findByZoneId(String zoneId);

    List<SurgeRule> findAllByZoneIdIn(List<String> zoneIds);

    List<SurgeRule> findByActiveDriversGreaterThan(Integer activeDrivers);

    List<SurgeRule> findBySurgeMultiplierGreaterThan(Double multiplier);

    void deleteByZoneId(String zoneId);
}
