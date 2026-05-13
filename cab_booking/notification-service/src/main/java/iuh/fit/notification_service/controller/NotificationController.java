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
    public org.springframework.data.domain.Page<Notification> getNotifications(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return notificationService.getNotificationsByUserId(userId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Notification getNotification(@PathVariable String id) {
        return notificationService.getNotificationById(id);
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
    }

    @PatchMapping("/user/{userId}/read-all")
    public void markAllAsRead(@PathVariable String userId) {
        notificationService.markAllAsRead(userId);
    }

    @DeleteMapping("/{id}")
    public void deleteNotification(@PathVariable String id) {
        notificationService.deleteNotification(id);
    }

    @PostMapping("/test")
    public Notification testNotification(@RequestParam String userId, @RequestParam String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title("Test Notification")
                .message(message)
                .type("PUSH")
                .status("SENT")
                .isRead(false)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        return notificationService.saveNotification(notification);
    }
}
