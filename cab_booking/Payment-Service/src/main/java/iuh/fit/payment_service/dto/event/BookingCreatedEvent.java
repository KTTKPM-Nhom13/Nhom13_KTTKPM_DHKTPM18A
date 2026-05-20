package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCreatedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("type")
    @JsonAlias({"eventType", "type"})
    private String type;

    @JsonProperty("bookingId")
    @JsonAlias({"rideId", "bookingId"})
    private String bookingId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("amount")
    @JsonAlias({"estimatedFare", "finalFare", "fare", "amount"})
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("pickup")
    private Map<String, Double> pickup;

    @JsonProperty("dropoff")
    private Map<String, Double> dropoff;

    @JsonProperty("timestamp")
    private String timestamp;
}
