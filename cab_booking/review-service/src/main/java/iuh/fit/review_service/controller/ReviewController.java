package iuh.fit.review_service.controller;

import iuh.fit.review_service.model.Review;
import iuh.fit.review_service.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final iuh.fit.review_service.repository.FinishedRideRepository finishedRideRepository;

    @PostMapping("/test/finish/{rideId}")
    public String mockFinishRide(@PathVariable String rideId) {
        iuh.fit.review_service.model.FinishedRide finishedRide = iuh.fit.review_service.model.FinishedRide.builder()
                .rideId(rideId)
                .finishedAt(java.time.LocalDateTime.now())
                .build();
        finishedRideRepository.save(finishedRide);
        return "Ride " + rideId + " marked as FINISHED. You can now create a review.";
    }

    @PostMapping
    public Review createReview(@RequestBody Review review) {
        return reviewService.createReview(review);
    }

    @GetMapping("/{id}")
    public Review getReview(@PathVariable String id) {
        return reviewService.getReviewById(id);
    }

    @GetMapping("/ride/{rideId}")
    public Review getReviewByRide(@PathVariable String rideId) {
        return reviewService.getReviewByRideId(rideId);
    }

    @GetMapping("/driver/{driverId}")
    public List<Review> getReviewsForDriver(@PathVariable String driverId) {
        return reviewService.getReviewsForDriver(driverId);
    }

    @GetMapping("/user/{userId}")
    public List<Review> getReviewsByUser(@PathVariable String userId) {
        return reviewService.getReviewsByUser(userId);
    }

    @PutMapping("/{id}")
    public Review updateReview(@PathVariable String id, @RequestBody Review review) {
        return reviewService.updateReview(id, review);
    }

    @DeleteMapping("/{id}")
    public void deleteReview(@PathVariable String id) {
        reviewService.deleteReview(id);
    }
}
