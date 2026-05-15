package iuh.fit.review_service.service;

import iuh.fit.review_service.model.Review;
import iuh.fit.review_service.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final iuh.fit.review_service.repository.FinishedRideRepository finishedRideRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Review createReview(Review review) {
        // Validation: Ensure only one review per ride
        reviewRepository.findByRideId(review.getRideId()).ifPresent(r -> {
            throw new RuntimeException("Review already exists for ride: " + review.getRideId());
        });
        
        review.setCreatedAt(LocalDateTime.now());
        Review savedReview = reviewRepository.save(review);

        // Send event to Kafka for driver-service to update rating
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("driverId", savedReview.getDriverId());
            event.put("rating", savedReview.getRating());
            event.put("rideId", savedReview.getRideId());
            event.put("timestamp", LocalDateTime.now().toString());
            
            kafkaTemplate.send("driver-reviews", event);
            log.info("Sent review event to Kafka for driver {}: {}", savedReview.getDriverId(), event);
        } catch (Exception e) {
            log.error("Failed to send review event to Kafka: {}", e.getMessage());
        }

        return savedReview;
    }

    public List<Review> getReviewsForDriver(String driverId) {
        return reviewRepository.findByDriverId(driverId);
    }

    public List<Review> getReviewsByUser(String userId) {
        return reviewRepository.findByUserId(userId);
    }

    public Review getReviewById(String id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));
    }

    public Review getReviewByRideId(String rideId) {
        return reviewRepository.findByRideId(rideId)
                .orElseThrow(() -> new RuntimeException("Review not found for ride"));
    }

    public Review updateReview(String id, Review reviewDetails) {
        Review review = getReviewById(id);
        review.setRating(reviewDetails.getRating());
        review.setComment(reviewDetails.getComment());
        return reviewRepository.save(review);
    }

    public void deleteReview(String id) {
        reviewRepository.deleteById(id);
    }
}
