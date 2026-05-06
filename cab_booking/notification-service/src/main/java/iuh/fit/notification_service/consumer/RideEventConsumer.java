package iuh.fit.notification_service.consumer;

import iuh.fit.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {
    private final NotificationService notificationService;

    @KafkaListener(topics = "ride-status-topic", groupId = "notification-group")
    public void consumeRideStatusEvent(Map<String, Object> event) {
        log.info("Consumed ride status event: {}", event);
        
        String userId = (String) event.get("userId");
        String status = (String) event.get("status");
        String rideId = (String) event.get("rideId");

        String title = "Ride Status Update";
        String message = String.format("Your ride %s is now %s", rideId, status);
        
        notificationService.sendNotification(userId, title, message, "PUSH");
    }
}
