package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideFinishedEvent {

    @JsonProperty("eventType")
    @JsonAlias({"type", "eventType"})
    private String eventType;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("rideId")
    @JsonAlias({"bookingId", "rideId"})
    private String rideId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("driverId")
    private String driverId;

    @JsonProperty("finalFare")
    private BigDecimal finalFare;

    @JsonProperty("distanceKm")
    private BigDecimal distanceKm;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    public String getEventId() {
        if (eventId == null || eventId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return eventId;
    }

    public String getEventType() {
        if (eventType == null || eventType.isBlank()) {
            return "RIDE_FINISHED";
        }
        return eventType;
    }
}
