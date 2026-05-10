package iuh.fit.notification_service.consumer;

import iuh.fit.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {
    private final NotificationService notificationService;

    @KafkaListener(topics = {"ride.created", "ride.assigned", "ride.finished"}, groupId = "notification-group")
    public void consumeRideEvents(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Consumed event from topic {}: {}", topic, event);
        
        String rideId = (String) event.get("rideId");
        String customerId = (String) event.get("customerId");
        String title = "Ride Update";
        String message = "";

        switch (topic) {
            case "ride.created":
                message = String.format("Your ride request %s has been created and is looking for a driver.", rideId);
                break;
            case "ride.assigned":
                String driverId = (String) event.get("driverId");
                message = String.format("Driver %s has been assigned to your ride %s.", driverId, rideId);
                break;
            case "ride.finished":
                message = String.format("Your ride %s has been completed. Hope you had a great trip!", rideId);
                break;
            default:
                message = "You have a new update for your ride.";
        }
        
        if (customerId != null) {
            notificationService.sendNotification(customerId, title, message, "PUSH");
        }
    }
}
