package iuh.fit.review_service.service;

import iuh.fit.review_service.model.Review;
import iuh.fit.review_service.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final iuh.fit.review_service.repository.FinishedRideRepository finishedRideRepository;

    public Review createReview(Review review) {
        // Validation: Ensure the ride is actually finished
        /* 
        // Temporarily disabled for testing
        if (!finishedRideRepository.existsById(review.getRideId())) {
            throw new IllegalArgumentException("Cannot review a ride that is not finished yet or does not exist. Please finish the ride first.");
        }
        */

        // Validation: Ensure only one review per ride
        reviewRepository.findByRideId(review.getRideId()).ifPresent(r -> {
            throw new RuntimeException("Review already exists for ride: " + review.getRideId());
        });
        
        review.setCreatedAt(LocalDateTime.now());
        return reviewRepository.save(review);
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
