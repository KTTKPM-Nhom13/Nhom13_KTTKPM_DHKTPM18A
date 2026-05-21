package iuh.fit.pricing_service.dto;

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
public class BulkZoneMetricsRequest {

    @NotNull(message = "Zone metrics list is required")
    private java.util.List<ZoneMetricEntry> zoneMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneMetricEntry {
        @NotNull(message = "Zone ID is required")
        private String zoneId;

        @NotNull(message = "Active drivers is required")
        @Positive(message = "Active drivers must be positive")
        private Integer activeDrivers;

        @NotNull(message = "Pending rides is required")
        @Positive(message = "Pending rides must be positive")
        private Integer pendingRides;
    }
}
