package iuh.fit.review_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RideFinishedConsumer {
    private final iuh.fit.review_service.repository.FinishedRideRepository finishedRideRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ride.finished", groupId = "review-group")
    public void consumeRideFinishedEvent(String message) {
        log.info("Consumed raw ride finished event: {}", message);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String rideId = (String) event.get("rideId");
            String customerId = (String) event.get("customerId");
            String driverId = (String) event.get("driverId");

            if (rideId != null) {
                iuh.fit.review_service.model.FinishedRide finishedRide = iuh.fit.review_service.model.FinishedRide.builder()
                        .rideId(rideId)
                        .customerId(customerId)
                        .driverId(driverId)
                        .finishedAt(java.time.LocalDateTime.now())
                        .build();
                finishedRideRepository.save(finishedRide);
                log.info("Persisted finished ride {} for review eligibility.", rideId);
            }
        } catch (Exception e) {
            log.error("Error processing RideFinished event: {}", e.getMessage());
        }
    }
}

