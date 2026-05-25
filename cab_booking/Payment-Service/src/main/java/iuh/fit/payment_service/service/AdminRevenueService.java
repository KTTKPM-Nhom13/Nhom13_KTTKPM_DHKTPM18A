package iuh.fit.payment_service.service;

import iuh.fit.payment_service.dto.response.*;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRevenueService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public AdminRevenueOverviewResponse getRevenueOverview(Instant startDate, Instant endDate) {
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();
        Instant effectiveStart = startDate != null ? startDate : effectiveEnd.minus(30, ChronoUnit.DAYS);

        log.info("[Service] Calculating revenue overview from {} to {}", effectiveStart, effectiveEnd);

        // Fetch current period data
        List<PaymentTransactionRepository.DailyRevenueProjection> dailyProjections =
                paymentTransactionRepository.computeDailyRevenue(effectiveStart, effectiveEnd);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTransactions = 0;
        List<DailyRevenueSummary> dailyBreakdown = new ArrayList<>();

        for (var projection : dailyProjections) {
            BigDecimal revenue = projection.getRevenue();
            Long trips = projection.getTrips();
            BigDecimal avg = projection.getAvgFare();

            totalRevenue = totalRevenue.add(revenue);
            totalTransactions += trips;

            dailyBreakdown.add(DailyRevenueSummary.builder()
                    .date(projection.getDateStr())
                    .revenue(revenue.setScale(2, RoundingMode.HALF_UP))
                    .transactionsCount(trips)
                    .averageAmount(avg.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        BigDecimal averageAmount = totalTransactions > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Fetch previous period data for growth rates
        Duration duration = Duration.between(effectiveStart, effectiveEnd);
        Instant prevStart = effectiveStart.minus(duration);
        Instant prevEnd = effectiveStart;

        List<PaymentTransactionRepository.DailyRevenueProjection> prevDailyProjections =
                paymentTransactionRepository.computeDailyRevenue(prevStart, prevEnd);

        BigDecimal prevTotalRevenue = BigDecimal.ZERO;
        long prevTotalTransactions = 0;

        for (var projection : prevDailyProjections) {
            prevTotalRevenue = prevTotalRevenue.add(projection.getRevenue());
            prevTotalTransactions += projection.getTrips();
        }

        BigDecimal revenueChangePercent = BigDecimal.ZERO;
        if (prevTotalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            revenueChangePercent = totalRevenue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        } else {
            revenueChangePercent = totalRevenue.subtract(prevTotalRevenue)
                    .divide(prevTotalRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        BigDecimal transactionsChangePercent = BigDecimal.ZERO;
        if (prevTotalTransactions == 0) {
            transactionsChangePercent = totalTransactions > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        } else {
            transactionsChangePercent = BigDecimal.valueOf(totalTransactions - prevTotalTransactions)
                    .divide(BigDecimal.valueOf(prevTotalTransactions), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // Fetch method breakdown
        List<PaymentTransactionRepository.MethodRevenueProjection> methodProjections =
                paymentTransactionRepository.computeMethodRevenue(effectiveStart, effectiveEnd);
        List<MethodRevenueSummary> methodBreakdown = new ArrayList<>();

        for (var projection : methodProjections) {
            BigDecimal percentage = BigDecimal.ZERO;
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = projection.getRevenue().divide(totalRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            methodBreakdown.add(MethodRevenueSummary.builder()
                    .paymentMethod(projection.getPaymentMethod())
                    .totalRevenue(projection.getRevenue().setScale(2, RoundingMode.HALF_UP))
                    .transactionsCount(projection.getTrips())
                    .percentage(percentage.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        // Fetch status breakdown
        List<PaymentTransactionRepository.StatusRevenueProjection> statusProjections =
                paymentTransactionRepository.computeStatusRevenue(effectiveStart, effectiveEnd);
        List<StatusRevenueSummary> statusBreakdown = new ArrayList<>();

        for (var projection : statusProjections) {
            statusBreakdown.add(StatusRevenueSummary.builder()
                    .status(projection.getStatus())
                    .totalAmount(projection.getRevenue().setScale(2, RoundingMode.HALF_UP))
                    .transactionsCount(projection.getTrips())
                    .build());
        }

        return AdminRevenueOverviewResponse.builder()
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalTransactions(totalTransactions)
                .averageTransactionAmount(averageAmount.setScale(2, RoundingMode.HALF_UP))
                .dailyBreakdown(dailyBreakdown)
                .methodBreakdown(methodBreakdown)
                .statusBreakdown(statusBreakdown)
                .revenueChangePercent(revenueChangePercent.setScale(2, RoundingMode.HALF_UP))
                .transactionsChangePercent(transactionsChangePercent.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public List<DailyRevenueSummary> getDailyRevenueBreakdown(Instant startDate, Instant endDate) {
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();
        Instant effectiveStart = startDate != null ? startDate : effectiveEnd.minus(30, ChronoUnit.DAYS);

        log.info("[Service] Getting daily revenue breakdown from {} to {}", effectiveStart, effectiveEnd);

        List<PaymentTransactionRepository.DailyRevenueProjection> projections =
                paymentTransactionRepository.computeDailyRevenue(effectiveStart, effectiveEnd);
        List<DailyRevenueSummary> result = new ArrayList<>();

        for (var projection : projections) {
            result.add(DailyRevenueSummary.builder()
                    .date(projection.getDateStr())
                    .revenue(projection.getRevenue().setScale(2, RoundingMode.HALF_UP))
                    .transactionsCount(projection.getTrips())
                    .averageAmount(projection.getAvgFare().setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return result;
    }

    public Page<PaymentTransaction> getTransactions(
            List<PaymentStatus> statuses,
            List<PaymentMethod> methods,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();
        Instant effectiveStart = startDate != null ? startDate : effectiveEnd.minus(30, ChronoUnit.DAYS);

        log.info("[Service] Listing payment transactions for admin with status filter={}, method filter={}, from {} to {}",
                statuses, methods, effectiveStart, effectiveEnd);

        boolean statusFilterActive = statuses != null && !statuses.isEmpty();
        boolean methodFilterActive = methods != null && !methods.isEmpty();

        return paymentTransactionRepository.findTransactionsForAdmin(
                statuses, statusFilterActive,
                methods, methodFilterActive,
                effectiveStart, effectiveEnd,
                pageable
        );
    }

    public AdminRevenueOverviewResponse getWeeklyRevenue() {
        Instant end = Instant.now();
        Instant start = end.minus(7, ChronoUnit.DAYS);
        return getRevenueOverview(start, end);
    }

    public AdminRevenueOverviewResponse getMonthlyRevenue() {
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        return getRevenueOverview(start, end);
    }

    public AdminRevenueOverviewResponse getDailyRevenueOverview(String dateStr) {
        log.info("[Service] Getting daily revenue overview for date: {}", dateStr);
        java.time.LocalDate localDate = java.time.LocalDate.parse(dateStr);
        Instant start = localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant end = localDate.atTime(java.time.LocalTime.MAX).atZone(java.time.ZoneOffset.UTC).toInstant();
        return getRevenueOverview(start, end);
    }
}
