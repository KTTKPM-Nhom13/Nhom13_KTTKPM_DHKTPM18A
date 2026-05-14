package iuh.fit.payment_service.entity;

import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_txn_id", columnList = "transactionId", unique = true),
        @Index(name = "idx_booking_id", columnList = "bookingId"),
        @Index(name = "idx_customer_id", columnList = "customerId"),
        @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(nullable = false, length = 64)
    private String bookingId;

    @Column(nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 255)
    private String gatewayTransactionId;

    @Column(length = 500)
    private String gatewayResponse;

    @Column(length = 2000)
    private String gatewayResponseJson;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 64)
    private String paymentGatewayName;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (transactionId == null) transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (status == null) status = PaymentStatus.INIT;
        if (retryCount < 0) retryCount = 0;
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (currency == null) currency = "VND";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markPending() {
        this.status = PaymentStatus.PENDING;
    }

    public void markSuccess(String gatewayTxnId, String gatewayResponse) {
        this.status = PaymentStatus.SUCCESS;
        this.gatewayTransactionId = gatewayTxnId;
        this.gatewayResponse = gatewayResponse;
    }

    public void markFailed(String reason, boolean shouldRetry) {
        this.failureReason = reason;
        this.retryCount++;
        if (shouldRetry && this.retryCount < 3) {
            this.status = PaymentStatus.RETRY;
        } else {
            this.status = PaymentStatus.FAILED_FINAL;
        }
    }

    public void markInit() {
        this.status = PaymentStatus.INIT;
    }

    public boolean canRetry() {
        return this.retryCount < 3 && (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.RETRY);
    }
}
