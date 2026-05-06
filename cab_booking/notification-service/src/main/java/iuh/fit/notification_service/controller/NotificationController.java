package iuh.fit.notification_service.controller;

import iuh.fit.notification_service.model.Notification;
import iuh.fit.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public List<Notification> getNotifications(@PathVariable String userId) {
        return notificationService.getNotificationsByUserId(userId);
    }

    @PostMapping("/test")
    public Notification testNotification(@RequestParam String userId, @RequestParam String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setCreatedAt(java.time.LocalDateTime.now());
        return notificationService.saveNotification(notification);
    }
}
