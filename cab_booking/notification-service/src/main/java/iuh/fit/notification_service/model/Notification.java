package iuh.fit.notification_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification {
    @Id
    private String id;
    private String userId;
    private String title;
    private String message;
    private String type; // PUSH, SMS, EMAIL
    private String status; // PENDING, SENT, FAILED
    private LocalDateTime createdAt;
}
