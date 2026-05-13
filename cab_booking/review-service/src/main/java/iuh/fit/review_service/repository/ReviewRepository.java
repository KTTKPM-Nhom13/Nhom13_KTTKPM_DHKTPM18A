package iuh.fit.review_service.repository;

import iuh.fit.review_service.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    List<Review> findByDriverId(String driverId);
    List<Review> findByUserId(String userId);
    java.util.Optional<Review> findByRideId(String rideId);
}
