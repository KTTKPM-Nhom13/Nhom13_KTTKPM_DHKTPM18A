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

    @KafkaListener(topics = {"ride.created", "ride.assigned", "ride.accepted", "ride.rejected", "ride.completed", "ride.arrived", "ride.started", "booking-events", "booking.timeout", "payment.completed", "pricing.surge.updated"}, groupId = "notification-group")
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
            
            String title = "Cập nhật chuyến đi";
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
                    String reason = String.valueOf(event.getOrDefault("reason", "Không xác định"));
                    if ("TIMEOUT_NO_DRIVER_FOUND".equals(reason) || reason.contains("TIMEOUT")) {
                        log.info("Skipping redundant RideCancelled notification for TIMEOUT, letting booking.timeout handle it.");
                        return;
                    }
                    
                    String viReason = reason;
                    String lowerReason = reason.toLowerCase();
                    if (lowerReason.contains("customer requested cancellation") || lowerReason.contains("customer requested") || lowerReason.contains("khách hàng")) {
                        viReason = "Khách hàng yêu cầu hủy";
                    } else if (lowerReason.contains("driver canceled") || lowerReason.contains("driver rejected") || lowerReason.contains("tài xế")) {
                        viReason = "Tài xế đã hủy chuyến đi";
                    } else if (lowerReason.contains("not specified") || reason.trim().isEmpty()) {
                        viReason = "Không xác định";
                    }
                    
                    notificationMessage = "Chuyến đi của bạn đã bị hủy. Lý do: " + viReason;
                }
            } else {
                switch (topic) {
                    case "ride.created":
                        notificationMessage = "Đang tìm tài xế gần nhất cho bạn...";
                        break;
                    case "ride.assigned":
                    case "ride.accepted":
                        String driverId = String.valueOf(event.getOrDefault("driverId", "unknown"));
                        notificationMessage = String.format("Đã tìm thấy tài xế! Tài xế đang đến điểm đón của bạn.");
                        break;
                    case "ride.rejected":
                        notificationMessage = "Tài xế đã từ chối. Đang tìm tài xế khác cho bạn...";
                        break;
                    case "booking.timeout":
                        notificationMessage = "Rất tiếc, hiện tại không tìm thấy tài xế nào xung quanh. Vui lòng thử lại sau ít phút.";
                        title = "Hết thời gian tìm kiếm";
                        break;
                    case "payment.completed":
                        notificationMessage = "Thanh toán thành công! Chúc bạn một ngày tốt lành.";
                        title = "Thanh toán thành công";
                        break;
                    case "ride.completed":
                        notificationMessage = "Chuyến đi đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!";
                        title = "Chuyến đi hoàn thành";
                        break;
                    case "pricing.surge.updated":
                        log.info("Surge pricing update received for zone: {}", event.get("zone_id"));
                        return;
                    default:
                        if (!type.isEmpty()) {
                            notificationMessage = "Cập nhật chuyến đi của bạn: " + type;
                        }
                }
            }
            
            if (customerId != null && !notificationMessage.isEmpty() && !"null".equals(customerId)) {
                notificationService.sendNotification(customerId, title, notificationMessage, "PUSH");
            }
            
            // Also broadcast the event to the active booking/ride room for real-time room routing (notifying driver & passenger simultaneously)
            if (rideId != null && !notificationMessage.isEmpty() && !"null".equals(rideId)) {
                notificationService.broadcastNotificationToRoom(rideId, title, notificationMessage, "ROOM_BROADCAST");
            }
        } catch (Exception e) {
            log.error("Error processing Kafka message from topic {}: {}", topic, e.getMessage());
        }
    }
}
