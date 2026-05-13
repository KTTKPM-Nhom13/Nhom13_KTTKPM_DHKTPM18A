package iuh.fit.review_service.repository;

import iuh.fit.review_service.model.FinishedRide;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinishedRideRepository extends MongoRepository<FinishedRide, String> {
}
