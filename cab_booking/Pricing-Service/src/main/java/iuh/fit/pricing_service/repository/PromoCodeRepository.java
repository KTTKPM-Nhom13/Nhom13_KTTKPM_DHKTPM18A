package iuh.fit.pricing_service.repository;

import iuh.fit.pricing_service.model.PromoCode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoCodeRepository extends MongoRepository<PromoCode, String> {
    Optional<PromoCode> findByCode(String code);
    boolean existsByCode(String code);
}
