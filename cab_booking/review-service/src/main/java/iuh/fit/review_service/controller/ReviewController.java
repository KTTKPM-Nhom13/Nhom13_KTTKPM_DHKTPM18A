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

    @PostMapping
    public Review createReview(@RequestBody Review review) {
        return reviewService.createReview(review);
    }

    @GetMapping("/driver/{driverId}")
    public List<Review> getReviewsForDriver(@PathVariable String driverId) {
        return reviewService.getReviewsForDriver(driverId);
    }

    @GetMapping("/user/{userId}")
    public List<Review> getReviewsByUser(@PathVariable String userId) {
        return reviewService.getReviewsByUser(userId);
    }
}
