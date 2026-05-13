package iuh.fit.notification_service.repository;

import iuh.fit.notification_service.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    org.springframework.data.domain.Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, org.springframework.data.domain.Pageable pageable);
    List<Notification> findByUserIdAndIsReadFalse(String userId);
}
