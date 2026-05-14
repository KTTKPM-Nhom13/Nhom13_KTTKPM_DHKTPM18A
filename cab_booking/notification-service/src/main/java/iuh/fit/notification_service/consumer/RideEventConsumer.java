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

    @KafkaListener(topics = {"ride.created", "ride.assigned", "ride.finished", "booking-events", "pricing.surge.updated"}, groupId = "notification-group")
    public void consumeRideEvents(Map<String, Object> event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Consumed event from topic {}: {}", topic, event);
        
        try {
            String rideId = (String) event.get("rideId");
            if (rideId == null) {
                log.warn("Missing rideId in event payload for topic {}", topic);
                return;
            }
            
            String customerId = (String) event.get("customerId");
            String title = "Ride Update";
            String message = "";

            if ("booking-events".equals(topic)) {
                String type = (String) event.get("type");
                if ("DriverArrived".equals(type)) {
                    message = "Your driver has arrived at the pickup location!";
                } else if ("RideStarted".equals(type)) {
                    message = "Your ride has started. Have a safe trip!";
                } else if ("RideCancelled".equals(type)) {
                    message = "Your ride has been cancelled. Reason: " + event.get("reason");
                }
            } else {
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
                        title = "Ride Completed";
                        break;
                    case "pricing.surge.updated":
                        message = "Surge pricing has been updated in your area.";
                        title = "Pricing Update";
                        break;
                    default:
                        message = "You have a new update for your ride.";
                }
            }
            
            if (customerId != null && !message.isEmpty()) {
                notificationService.sendNotification(customerId, title, message, "PUSH");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", e.getMessage());
        }
    }
}
