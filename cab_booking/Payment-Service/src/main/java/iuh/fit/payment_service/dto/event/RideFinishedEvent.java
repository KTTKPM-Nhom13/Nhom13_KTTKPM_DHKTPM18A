package iuh.fit.payment_service.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideFinishedEvent {

    public static final String EVENT_TYPE = "RIDE_FINISHED";
    public static final String SCHEMA_VERSION = "1.0.0";

    private String eventId;
    private String type;
    private String rideId;
    private String customerId;
    private String driverId;
    private BigDecimal totalAmount;
    private BigDecimal baseFare;
    private BigDecimal distanceKm;
    private BigDecimal durationMinutes;
    private BigDecimal surgeMultiplier;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String currency;
    private String paymentMethod;
    private String bookingId;
    private Instant completedAt;
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
}
