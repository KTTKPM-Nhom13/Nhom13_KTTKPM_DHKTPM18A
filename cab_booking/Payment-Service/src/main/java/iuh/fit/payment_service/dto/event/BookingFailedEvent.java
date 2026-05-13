package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingFailedEvent {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("failedAt")
    private Long failedAt;

    public String getEventType() {
        if (eventType == null || eventType.isBlank()) {
            return "BOOKING_FAILED";
        }
        return eventType;
    }

    public String getEventId() {
        if (eventId == null || eventId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return eventId;
    }
}
