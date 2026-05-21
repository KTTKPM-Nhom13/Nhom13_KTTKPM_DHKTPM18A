package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    @JsonAlias({"eventType", "type"})
    private String eventType;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("amount")
    @JsonAlias({"estimatedFare", "finalFare", "fare", "amount"})
    private BigDecimal amount;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("paymentPhase")
    private String paymentPhase;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timestamp")
    private String timestamp;
}
