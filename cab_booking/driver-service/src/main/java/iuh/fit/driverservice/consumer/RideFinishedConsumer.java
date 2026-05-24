package iuh.fit.driverservice.consumer;

import iuh.fit.driverservice.dto.event.RideFinishedEvent;
import iuh.fit.driverservice.service.DriverEarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ride.finished events with typed DTO.
 * Deserialization handled by JsonDeserializer via type mapping in application.yaml.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RideFinishedConsumer {

    private final DriverEarningService driverEarningService;

    @KafkaListener(topics = "ride.finished", groupId = "driver-service-finished-group")
    public void consumeRideFinished(RideFinishedEvent event) {
        try {
            log.info("[DriverEarning] Consumed ride.finished - eventId={}, rideId={}, driverId={}, amount={}, finalFare={}",
                    event.getEventId(), event.getRideId(), event.getDriverId(), event.getAmount(), event.getFinalFare());
            driverEarningService.creditDriverFromRideFinished(event);
        } catch (Exception e) {
            log.error("[DriverEarning] Failed to process ride.finished: {}", e.getMessage(), e);
            throw e;
        }
    }
}
