package iuh.fit.pricing_service.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SurgeUpdateRequest {

    @NotNull(message = "Multiplier is required")
    private BigDecimal multiplier;
}
