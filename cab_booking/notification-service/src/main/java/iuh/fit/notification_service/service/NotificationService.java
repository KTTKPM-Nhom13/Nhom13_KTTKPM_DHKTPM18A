package iuh.fit.notification_service.service;

import iuh.fit.notification_service.model.Notification;
import iuh.fit.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SocketIOService socketIOService;

    // Thread-safe map to store user FCM tokens (userId -> fcmToken)
    private static final java.util.Map<String, String> userFcmTokens = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Registers a device FCM token for a user
     */
    public void registerFcmToken(String userId, String token) {
        if (userId != null && token != null) {
            userFcmTokens.put(userId, token);
            log.info("Registered FCM token for user {}: {}", userId, token);
        }
    }

    public void sendNotification(String userId, String title, String message, String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .status("SENT")
                .createdAt(LocalDateTime.now())
                .build();
        
        notificationRepository.save(notification);
        log.info("Notification sent to user {}: {}", userId, message);
        
        // 1. Push real-time notification via Socket.io (Foreground)
        try {
            socketIOService.sendNotification(userId, "new_notification", notification);
        } catch (Exception e) {
            log.error("Failed to push real-time notification to user {}: {}", userId, e.getMessage());
        }

        // 2. Push background notification via Firebase FCM (Background/Killed)
        try {
            String fcmToken = userFcmTokens.get(userId);
            if (fcmToken != null && !fcmToken.trim().isEmpty()) {
                sendPushNotification(fcmToken, title, message);
            } else {
                log.debug("No FCM token registered for user {}, skipping push notification.", userId);
            }
        } catch (Exception e) {
            log.error("Failed to send Firebase FCM push to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Sends a background push notification via Firebase Admin SDK
     */
    public void sendPushNotification(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.trim().isEmpty() || "null".equals(fcmToken)) {
            log.warn("FCM Token is empty, skipping push notification.");
            return;
        }

        try {
            com.google.firebase.messaging.Notification fcmNotification = com.google.firebase.messaging.Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            com.google.firebase.messaging.Message message = com.google.firebase.messaging.Message.builder()
                    .setToken(fcmToken)
                    .setNotification(fcmNotification)
                    .build();

            String response = com.google.firebase.messaging.FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message to token: {}. Response: {}", fcmToken, response);
        } catch (Exception e) {
            log.error("Failed to send FCM message: {}", e.getMessage());
        }
    }

    /**
     * Broadcasts a notification to a specific ride/booking room (notifying both passenger & driver)
     */
    public void broadcastNotificationToRoom(String bookingId, String title, String message, String type) {
        Notification notification = Notification.builder()
                .userId("ROOM_" + bookingId)
                .title(title)
                .message(message)
                .type(type)
                .status("BROADCASTED")
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification broadcasted to booking room {}: {}", bookingId, message);

        try {
            socketIOService.broadcastToBookingRoom(bookingId, "new_notification", notification);
        } catch (Exception e) {
            log.error("Failed to broadcast real-time notification to booking room {}: {}", bookingId, e.getMessage());
        }
    }

    public org.springframework.data.domain.Page<Notification> getNotificationsByUserId(String userId, org.springframework.data.domain.Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Notification getNotificationById(String id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    public void markAsRead(String id) {
        Notification notification = getNotificationById(id);
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public void markAllAsRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
        });
        notificationRepository.saveAll(unread);
    }

    public void deleteNotification(String id) {
        notificationRepository.deleteById(id);
    }

    public Notification saveNotification(Notification notification) {
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(LocalDateTime.now());
        }
        return notificationRepository.save(notification);
    }
}
