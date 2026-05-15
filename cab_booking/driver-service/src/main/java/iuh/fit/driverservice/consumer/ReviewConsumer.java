package iuh.fit.driverservice.consumer;

import iuh.fit.driverservice.service.DriverProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewConsumer {
    private final DriverProfileService driverProfileService;

    @KafkaListener(topics = "driver-reviews", groupId = "driver-service-review-group")
    public void consumeReviewEvent(Map<String, Object> event) {
        log.info("Consumed review event: {}", event);
        
        try {
            String driverId = (String) event.get("driverId");
            Integer rating = (Integer) event.get("rating");

            if (driverId != null && rating != null) {
                driverProfileService.updateDriverRating(driverId, rating);
                log.info("Successfully updated rating for driver: {}", driverId);
            }
        } catch (Exception e) {
            log.error("Error processing review event: {}", e.getMessage());
        }
    }
}
