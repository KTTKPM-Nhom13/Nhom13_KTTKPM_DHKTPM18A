package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatisticsResponse {

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private BigDecimal totalRevenue;
    private Long totalTrips;
    private BigDecimal averageFare;

    private List<DailyRevenueSummary> dailyBreakdown;
    private List<ZoneRevenueSummary> zoneBreakdown;
    private List<VehicleTypeRevenueSummary> vehicleTypeBreakdown;

    private BigDecimal totalBaseFare;
    private BigDecimal totalDistanceFare;
    private BigDecimal totalTimeFare;
    private BigDecimal totalSurgeRevenue;
    private BigDecimal totalPlatformFees;

    private BigDecimal revenueChangePercent;
    private BigDecimal tripsChangePercent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenueSummary {
        private String date;
        private BigDecimal revenue;
        private Long trips;
        private BigDecimal averageFare;
        private BigDecimal peakHourRevenue;
        private Long peakHourTrips;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneRevenueSummary {
        private String zoneId;
        private BigDecimal revenue;
        private Long trips;
        private BigDecimal averageFare;
        private BigDecimal percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleTypeRevenueSummary {
        private String vehicleType;
        private BigDecimal revenue;
        private Long trips;
        private BigDecimal averageFare;
        private BigDecimal percentageOfTotal;
    }
}
