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
public class PaymentCompletedEvent {

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

    @JsonProperty("driverId")
    private String driverId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("gatewayTransactionId")
    private String gatewayTransactionId;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("timestamp")
    private String timestamp;

    public static PaymentCompletedEvent fromTransaction(
            String rideId,
            String driverId,
            BigDecimal amount,
            String currency,
            String gatewayTransactionId,
            String paymentMethod) {
        return PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("PaymentCompleted")
                .eventType("PAYMENT_COMPLETED")
                .rideId(rideId)
                .bookingId(rideId)
                .driverId(driverId)
                .amount(amount)
                .currency(currency)
                .gatewayTransactionId(gatewayTransactionId)
                .paymentMethod(paymentMethod)
                .timestamp(Instant.now().toString())
                .build();
    }
}
