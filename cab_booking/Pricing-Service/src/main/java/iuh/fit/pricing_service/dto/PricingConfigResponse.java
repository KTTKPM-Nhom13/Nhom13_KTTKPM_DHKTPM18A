package iuh.fit.pricing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfigResponse {

    private String id;
    private String vehicleType;
    private Double baseFare;
    private Double perKmRate;
    private Double perMinuteRate;
    private Double multiplier;
    private Boolean active;
    private LocalDateTime updatedAt;
    private String schemaVersion;
}
