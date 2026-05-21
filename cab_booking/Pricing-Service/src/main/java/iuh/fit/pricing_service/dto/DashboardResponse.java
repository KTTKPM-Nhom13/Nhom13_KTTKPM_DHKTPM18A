package iuh.fit.pricing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private int totalPricingConfigs;
    private int totalSurgeRules;
    private int activeZones;
    private Map<String, Object> configSummary;
    private Map<String, Object> surgeSummary;
}
