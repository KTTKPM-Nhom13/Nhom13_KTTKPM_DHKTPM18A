package iuh.fit.pricing_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurgeRuleRequest {

    private String zoneId;

    private String zoneName;

    @NotNull(message = "Surge multiplier is required")
    @DecimalMin(value = "1.0", message = "Surge multiplier must be at least 1.0")
    @DecimalMax(value = "5.0", message = "Surge multiplier must not exceed 5.0")
    private Double surgeMultiplier;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    private Double radiusKm;

    private Integer activeDrivers;

    private Integer pendingRides;

    private Double minMultiplier;

    private Double maxMultiplier;

    private String source;
}
