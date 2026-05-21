package iuh.fit.review_service.consumer;

import iuh.fit.review_service.model.FinishedRide;
import iuh.fit.review_service.repository.FinishedRideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RideCompletedConsumer {
    private final FinishedRideRepository finishedRideRepository;

    @KafkaListener(topics = "ride.completed", groupId = "review-group")
    public void consumeRideCompletedEvent(Map<String, Object> event) {
        log.info("Consumed ride completed event: {}", event);
        
        try {
            if (event != null) {
                String rideId = (String) event.get("rideId");
                String customerId = (String) event.get("customerId");
                String driverId = (String) event.get("driverId");

                if (rideId != null) {
                    FinishedRide finishedRide = FinishedRide.builder()
                            .rideId(rideId)
                            .customerId(customerId)
                            .driverId(driverId)
                            .finishedAt(LocalDateTime.now())
                            .build();
                    finishedRideRepository.save(finishedRide);
                    log.info("Persisted completed ride {} for review eligibility.", rideId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing ride.completed event: {}", e.getMessage());
        }
    }
}
