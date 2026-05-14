package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundedEvent {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("refundTransactionId")
    private String refundTransactionId;

    @JsonProperty("reason")
    private String reason;

    public static PaymentRefundedEvent fromTransaction(
            String rideId,
            BigDecimal amount,
            String currency,
            String refundTransactionId,
            String reason) {
        return PaymentRefundedEvent.builder()
                .eventType("PAYMENT_REFUNDED")
                .eventId(java.util.UUID.randomUUID().toString())
                .rideId(rideId)
                .status("REFUNDED")
                .amount(amount)
                .refundTransactionId(refundTransactionId)
                .reason(reason)
                .build();
    }
}
