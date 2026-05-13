package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private BigDecimal amount;

    public static PaymentCompletedEvent fromTransaction(
            String bookingId,
            BigDecimal amount,
            String currency,
            String gatewayTransactionId,
            String paymentMethod) {
        return PaymentCompletedEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .bookingId(bookingId)
                .status("SUCCESS")
                .amount(amount)
                .build();
    }
}
