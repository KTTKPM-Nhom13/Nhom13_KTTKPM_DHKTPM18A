package iuh.fit.payment_service.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {

    public static final String EVENT_TYPE = "PAYMENT_FAILED";
    public static final String SCHEMA_VERSION = "1.0.0";

    private String eventId;
    private String type;
    private String transactionId;
    private String rideId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
    private int retryCount;
    private Instant failedAt;
    private String schemaVersion;

    public String getEventId() {
        if (eventId == null || eventId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return eventId;
    }

    public String getType() {
        if (type == null || type.isBlank()) {
            return EVENT_TYPE;
        }
        return type;
    }

    public String getSchemaVersion() {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return SCHEMA_VERSION;
        }
        return schemaVersion;
    }

    public static PaymentFailedEvent fromTransaction(
            String transactionId,
            String rideId,
            String customerId,
            BigDecimal amount,
            String currency,
            String failureReason,
            int retryCount) {
        return PaymentFailedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .type(EVENT_TYPE)
                .transactionId(transactionId)
                .rideId(rideId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .status("failed")
                .failureReason(failureReason)
                .retryCount(retryCount)
                .failedAt(Instant.now())
                .schemaVersion(SCHEMA_VERSION)
                .build();
    }
}
