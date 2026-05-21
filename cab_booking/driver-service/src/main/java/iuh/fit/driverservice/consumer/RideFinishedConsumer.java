package iuh.fit.driverservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.driverservice.dto.event.RideFinishedEvent;
import iuh.fit.driverservice.service.DriverEarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideFinishedConsumer {

    private final DriverEarningService driverEarningService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ride.finished", groupId = "driver-service-finished-group")
    public void consumeRideFinished(@Payload Map<String, Object> payload) {
        try {
            RideFinishedEvent event = objectMapper.convertValue(payload, RideFinishedEvent.class);
            log.info("[DriverEarning] Consumed ride.finished - eventId={}, rideId={}, driverId={}, amount={}, finalFare={}",
                    event.getEventId(), event.getRideId(), event.getDriverId(), event.getAmount(), event.getFinalFare());
            driverEarningService.creditDriverFromRideFinished(event);
        } catch (Exception e) {
            log.error("[DriverEarning] Failed to process ride.finished: {}", e.getMessage(), e);
            throw e;
        }
    }
}
