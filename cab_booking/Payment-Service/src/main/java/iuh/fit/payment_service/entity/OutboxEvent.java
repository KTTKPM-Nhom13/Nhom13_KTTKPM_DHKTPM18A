package iuh.fit.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_aggregate_status", columnList = "aggregateType, status"),
        @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private int retryCount;

    public enum OutboxStatus {
        PENDING,
        SENT,
        FAILED
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = OutboxStatus.PENDING;
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (retryCount < 0) retryCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.errorMessage = error;
        if (this.retryCount >= 5) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public boolean canRetry() {
        return this.retryCount < 5 && this.status == OutboxStatus.PENDING;
    }
}
