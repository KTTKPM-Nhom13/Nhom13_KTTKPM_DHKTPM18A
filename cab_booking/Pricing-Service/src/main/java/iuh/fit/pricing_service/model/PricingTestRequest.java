package iuh.fit.pricing_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PricingTestRequest {

    @NotNull(message = "Distance in km is required")
    @Min(value = 0, message = "Distance must be positive")
    private Double distanceKm;

    @NotNull(message = "Demand index is required")
    @Min(value = 0, message = "Demand index must be positive")
    private Double demandIndex;
}
