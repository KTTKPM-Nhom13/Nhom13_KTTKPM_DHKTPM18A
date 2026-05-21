package iuh.fit.pricing_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfigRequest {

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;

    @NotNull(message = "Base fare is required")
    @Positive(message = "Base fare must be positive")
    private Double baseFare;

    @NotNull(message = "Per km rate is required")
    @Positive(message = "Per km rate must be positive")
    private Double perKmRate;

    @NotNull(message = "Per minute rate is required")
    @Positive(message = "Per minute rate must be positive")
    private Double perMinuteRate;

    private Double multiplier;

    private Boolean active;
}
