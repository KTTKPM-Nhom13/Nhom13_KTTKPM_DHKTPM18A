package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "surge_rules")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SurgeRule {

    @Id
    private String id;

    @Indexed
    private String zoneId;

    private String zoneName;

    private BigDecimal surgeMultiplier;

    private Double latitude;

    private Double longitude;

    private Double radiusKm;

    private Integer activeDrivers;

    private Integer pendingRides;

    private Double demandScore;

    private BigDecimal minMultiplier;

    private BigDecimal maxMultiplier;

    private LocalDateTime lastUpdated;

    private LocalDateTime createdAt;

    private String source;

    private String schemaVersion;

    public enum SurgeSource {
        AUTOMATIC,
        MANUAL,
        AI_PREDICTED,
        EVENT_BASED
    }
}
