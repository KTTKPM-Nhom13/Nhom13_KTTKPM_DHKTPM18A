package iuh.fit.pricing_service.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PricingEvent {

    private String eventId;

    private String eventType;

    private String zoneId;

    private BigDecimal surgeMultiplier;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String schemaVersion;

    private String source;

    private Object metadata;

    public enum EventType {
        SURGE_UPDATED,
        FARE_CALCULATED,
        FARE_CONFIRMED,
        PRICING_ALERT
    }
}
