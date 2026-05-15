package iuh.fit.notification_service.consumer;

import iuh.fit.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"ride.created", "ride.assigned", "ride.finished", "ride.arrived", "ride.started", "booking-events", "pricing.surge.updated"}, groupId = "notification-group")
    public void consumeRideEvents(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Consumed raw event from topic {}: {}", topic, message);
        
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            // Safe extraction with fallback to different field names
            String rideId = event.containsKey("rideId") ? String.valueOf(event.get("rideId")) : null;
            String customerId = event.containsKey("customerId") ? String.valueOf(event.get("customerId")) : null;

            if (rideId == null && !"pricing.surge.updated".equals(topic)) {
                log.debug("Skipping event from topic {} as it has no rideId", topic);
                return;
            }
            
            String title = "Ride Update";
            String notificationMessage = "";
            
            // Get event type from either 'type' or 'eventType' field
            String type = event.containsKey("type") ? String.valueOf(event.get("type")) : 
                         (event.containsKey("eventType") ? String.valueOf(event.get("eventType")) : "");

            if ("booking-events".equals(topic) || "ride.arrived".equals(topic) || "ride.started".equals(topic)) {
                if ("DriverArrived".equals(type) || "RIDE_ARRIVED".equals(type) || "ride.arrived".equals(topic)) {
                    notificationMessage = "Tài xế đã đến điểm đón!";
                } else if ("RideStarted".equals(type) || "RIDE_STARTED".equals(type) || "ride.started".equals(topic)) {
                    notificationMessage = "Chuyến đi đã bắt đầu. Chúc bạn một chuyến đi an toàn!";
                } else if ("RideCancelled".equals(type) || "RIDE_CANCELLED".equals(type)) {
                    notificationMessage = "Your ride has been cancelled. Reason: " + event.getOrDefault("reason", "Not specified");
                }
            } else {
                switch (topic) {
                    case "ride.created":
                        notificationMessage = "Đang tìm tài xế gần nhất cho bạn...";
                        break;
                    case "ride.assigned":
                        String driverId = String.valueOf(event.getOrDefault("driverId", "unknown"));
                        notificationMessage = String.format("Đã tìm thấy tài xế! Tài xế đang đến điểm đón của bạn.");
                        break;
                    case "ride.finished":
                        notificationMessage = "Chuyến đi đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!";
                        title = "Ride Completed";
                        break;
                    case "pricing.surge.updated":
                        log.info("Surge pricing update received for zone: {}", event.get("zone_id"));
                        return;
                    default:
                        if (!type.isEmpty()) {
                            notificationMessage = "Update for your ride: " + type;
                        }
                }
            }
            
            if (customerId != null && !notificationMessage.isEmpty() && !"null".equals(customerId)) {
                notificationService.sendNotification(customerId, title, notificationMessage, "PUSH");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message from topic {}: {}", topic, e.getMessage());
        }
    }
}
