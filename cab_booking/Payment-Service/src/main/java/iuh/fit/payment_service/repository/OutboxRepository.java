package iuh.fit.payment_service.repository;

import iuh.fit.payment_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingEventsWithLimit(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.createdAt < :cutoff ORDER BY o.createdAt ASC")
    List<OutboxEvent> findStalePendingEvents(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'SENT' WHERE o.id = :id")
    void markAsSent(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'SENT' AND o.updatedAt < :before")
    int deleteOldSentEvents(@Param("before") Instant before);
}
