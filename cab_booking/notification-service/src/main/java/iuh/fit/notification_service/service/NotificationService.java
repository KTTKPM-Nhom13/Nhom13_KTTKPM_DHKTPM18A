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
        // Here we could integrate with Firebase (FCM) or Twilio (SMS)
    }

    public List<Notification> getNotificationsByUserId(String userId) {
        return notificationRepository.findByUserId(userId);
    }

    public Notification saveNotification(Notification notification) {
        return notificationRepository.save(notification);
    }
}
