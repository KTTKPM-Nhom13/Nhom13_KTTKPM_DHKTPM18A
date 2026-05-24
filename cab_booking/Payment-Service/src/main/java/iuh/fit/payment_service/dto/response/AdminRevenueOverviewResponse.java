package iuh.fit.payment_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRevenueOverviewResponse {
    private Instant startDate;
    private Instant endDate;
    private BigDecimal totalRevenue;
    private Long totalTransactions;
    private BigDecimal averageTransactionAmount;
    private List<DailyRevenueSummary> dailyBreakdown;
    private List<MethodRevenueSummary> methodBreakdown;
    private List<StatusRevenueSummary> statusBreakdown;
    private BigDecimal revenueChangePercent;
    private BigDecimal transactionsChangePercent;
}
