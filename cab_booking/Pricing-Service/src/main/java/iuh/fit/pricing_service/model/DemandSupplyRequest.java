package iuh.fit.pricing_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DemandSupplyRequest {

    @NotBlank(message = "Zone ID is required")
    private String zoneId;

    @NotNull(message = "Active drivers count is required")
    @Min(value = 0, message = "Active drivers must be non-negative")
    private Integer activeDrivers;

    @NotNull(message = "Pending rides count is required")
    @Min(value = 0, message = "Pending rides must be non-negative")
    private Integer pendingRides;
}
