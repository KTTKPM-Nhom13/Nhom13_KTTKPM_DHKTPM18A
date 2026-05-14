package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("timestamp")
    private String timestamp;

    public static PaymentFailedEvent fromTransaction(
            String bookingId,
            BigDecimal amount,
            String currency,
            String failureReason,
            int retryCount) {
        return PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("PaymentFailed")
                .eventType("PAYMENT_FAILED")
                .rideId(bookingId)
                .bookingId(bookingId)
                .status("FAILED")
                .reason(failureReason)
                .timestamp(Instant.now().toString())
                .build();
    }
}
