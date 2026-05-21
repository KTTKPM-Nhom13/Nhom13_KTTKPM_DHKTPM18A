package iuh.fit.pricing_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurgeRuleRequest {

    @NotBlank(message = "Zone ID is required")
    private String zoneId;

    private String zoneName;

    @NotNull(message = "Surge multiplier is required")
    @DecimalMin(value = "1.0", message = "Surge multiplier must be at least 1.0")
    @DecimalMax(value = "5.0", message = "Surge multiplier must not exceed 5.0")
    private Double surgeMultiplier;

    private Double latitude;

    private Double longitude;

    private Double radiusKm;

    private Integer activeDrivers;

    private Integer pendingRides;

    private Double minMultiplier;

    private Double maxMultiplier;

    private String source;
}
