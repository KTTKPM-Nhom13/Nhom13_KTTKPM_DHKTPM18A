package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reason")
    private String reason;

    public static PaymentFailedEvent fromTransaction(
            String rideId,
            BigDecimal amount,
            String currency,
            String failureReason,
            int retryCount) {
        return PaymentFailedEvent.builder()
                .eventType("PAYMENT_FAILED")
                .rideId(rideId)
                .status("FAILED")
                .reason(failureReason)
                .build();
    }
}
