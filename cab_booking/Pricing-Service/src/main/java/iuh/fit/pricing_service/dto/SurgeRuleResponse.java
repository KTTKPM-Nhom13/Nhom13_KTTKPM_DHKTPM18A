package iuh.fit.pricing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurgeRuleResponse {

    private String id;
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
}
