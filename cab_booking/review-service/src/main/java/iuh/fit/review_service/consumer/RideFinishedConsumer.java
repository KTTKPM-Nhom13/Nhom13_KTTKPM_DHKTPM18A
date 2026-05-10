package iuh.fit.review_service.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class RideFinishedConsumer {

    @KafkaListener(topics = "ride.finished", groupId = "review-group")
    public void consumeRideFinishedEvent(Map<String, Object> event) {
        log.info("Consumed ride finished event: {}", event);

        String rideId = (String) event.get("rideId");
        String customerId = (String) event.get("customerId");

        // In a real system, you might create a placeholder for a review
        // or notify the user to leave a review.
        log.info("Ride {} finished for customer {}. Ready for review.", rideId, customerId);
    }
}
