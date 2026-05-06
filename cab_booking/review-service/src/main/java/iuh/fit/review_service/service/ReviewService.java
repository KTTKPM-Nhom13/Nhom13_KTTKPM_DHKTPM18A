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

    public Review createReview(Review review) {
        review.setCreatedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public List<Review> getReviewsForDriver(String driverId) {
        return reviewRepository.findByDriverId(driverId);
    }

    public List<Review> getReviewsByUser(String userId) {
        return reviewRepository.findByUserId(userId);
    }
}
