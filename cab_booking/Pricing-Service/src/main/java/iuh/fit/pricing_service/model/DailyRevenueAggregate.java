package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "revenue_aggregations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueAggregate {

    @Id
    private String id;

    private String date;

    private BigDecimal totalRevenue;

    private Long totalTrips;

    private BigDecimal averageFare;

    private BigDecimal totalBaseFare;

    private BigDecimal totalDistanceFare;

    private BigDecimal totalTimeFare;

    private BigDecimal totalSurgeRevenue;

    private BigDecimal totalPlatformFees;

    private BigDecimal peakHourRevenue;

    private Long peakHourTrips;

    private LocalDateTime computedAt;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}
