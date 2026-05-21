package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.model.RevenueStatisticsResponse;
import iuh.fit.pricing_service.repository.FareEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RevenueStatisticsService {

    private final FareEstimateRepository fareEstimateRepository;

    public RevenueStatisticsResponse getRevenueStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveStart = startDate != null ? startDate : now.minusDays(7);
        LocalDateTime effectiveEnd = endDate != null ? endDate : now;

        LocalDateTime normalizedStart = effectiveStart.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime normalizedEnd = effectiveEnd.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        log.info("Computing revenue statistics from {} to {}", normalizedStart, normalizedEnd);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        Long totalTrips = 0L;
        BigDecimal averageFare = BigDecimal.ZERO;

        var mainAggregation = fareEstimateRepository.computeRevenueAggregation(normalizedStart, normalizedEnd);
        if (mainAggregation != null) {
            totalRevenue = mainAggregation.getTotalRevenue() != null ? mainAggregation.getTotalRevenue() : BigDecimal.ZERO;
            totalTrips = mainAggregation.getTotalTrips() != null ? mainAggregation.getTotalTrips() : 0L;
            averageFare = mainAggregation.getAvgFare() != null ? mainAggregation.getAvgFare() : BigDecimal.ZERO;
        }

        List<RevenueStatisticsResponse.DailyRevenueSummary> dailyBreakdown = new ArrayList<>();
        List<RevenueStatisticsResponse.ZoneRevenueSummary> zoneBreakdown = new ArrayList<>();
        List<RevenueStatisticsResponse.VehicleTypeRevenueSummary> vehicleTypeBreakdown = new ArrayList<>();

        List<FareEstimateRepository.DailyAggregation> dailyData = fareEstimateRepository.computeDailyAggregations(normalizedStart, normalizedEnd);
        for (var daily : dailyData) {
            dailyBreakdown.add(RevenueStatisticsResponse.DailyRevenueSummary.builder()
                    .date(daily.getId())
                    .revenue(daily.getRevenue() != null ? daily.getRevenue() : BigDecimal.ZERO)
                    .trips(daily.getTrips() != null ? daily.getTrips() : 0L)
                    .averageFare(daily.getAvgFare() != null ? daily.getAvgFare() : BigDecimal.ZERO)
                    .build());
        }

        List<FareEstimateRepository.ZoneAggregation> zoneData = fareEstimateRepository.computeZoneAggregations(normalizedStart, normalizedEnd);
        for (var zone : zoneData) {
            BigDecimal zoneRevenue = zone.getRevenue() != null ? zone.getRevenue() : BigDecimal.ZERO;
            BigDecimal percentage = BigDecimal.ZERO;
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = zoneRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            zoneBreakdown.add(RevenueStatisticsResponse.ZoneRevenueSummary.builder()
                    .zoneId(zone.getId())
                    .revenue(zoneRevenue)
                    .trips(zone.getTrips() != null ? zone.getTrips() : 0L)
                    .averageFare(zone.getAvgFare() != null ? zone.getAvgFare() : BigDecimal.ZERO)
                    .percentageOfTotal(percentage.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        List<FareEstimateRepository.VehicleTypeAggregation> vehicleData = fareEstimateRepository.computeVehicleTypeAggregations(normalizedStart, normalizedEnd);
        for (var vehicle : vehicleData) {
            BigDecimal vehicleRevenue = vehicle.getRevenue() != null ? vehicle.getRevenue() : BigDecimal.ZERO;
            BigDecimal percentage = BigDecimal.ZERO;
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = vehicleRevenue.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            vehicleTypeBreakdown.add(RevenueStatisticsResponse.VehicleTypeRevenueSummary.builder()
                    .vehicleType(vehicle.getId())
                    .revenue(vehicleRevenue)
                    .trips(vehicle.getTrips() != null ? vehicle.getTrips() : 0L)
                    .averageFare(vehicle.getAvgFare() != null ? vehicle.getAvgFare() : BigDecimal.ZERO)
                    .percentageOfTotal(percentage.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        BigDecimal totalBaseFare = BigDecimal.ZERO;
        BigDecimal totalDistanceFare = BigDecimal.ZERO;
        BigDecimal totalTimeFare = BigDecimal.ZERO;
        BigDecimal totalSurgeRevenue = BigDecimal.ZERO;
        BigDecimal totalPlatformFees = BigDecimal.ZERO;

        var fareComponents = fareEstimateRepository.computeFareComponents(normalizedStart, normalizedEnd);
        if (fareComponents != null) {
            totalBaseFare = fareComponents.getTotalBaseFare() != null ? fareComponents.getTotalBaseFare() : BigDecimal.ZERO;
            totalDistanceFare = fareComponents.getTotalDistanceFare() != null ? fareComponents.getTotalDistanceFare() : BigDecimal.ZERO;
            totalTimeFare = fareComponents.getTotalTimeFare() != null ? fareComponents.getTotalTimeFare() : BigDecimal.ZERO;
            totalSurgeRevenue = fareComponents.getTotalSurge() != null ? fareComponents.getTotalSurge() : BigDecimal.ZERO;
            totalPlatformFees = fareComponents.getTotalPlatformFees() != null ? fareComponents.getTotalPlatformFees() : BigDecimal.ZERO;
        }

        BigDecimal revenueChangePercent = calculateRevenueChange(normalizedStart, normalizedEnd);
        BigDecimal tripsChangePercent = calculateTripsChange(normalizedStart, normalizedEnd);

        return RevenueStatisticsResponse.builder()
                .startDate(normalizedStart)
                .endDate(normalizedEnd)
                .totalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalTrips(totalTrips)
                .averageFare(averageFare.setScale(2, RoundingMode.HALF_UP))
                .dailyBreakdown(dailyBreakdown)
                .zoneBreakdown(zoneBreakdown)
                .vehicleTypeBreakdown(vehicleTypeBreakdown)
                .totalBaseFare(totalBaseFare.setScale(2, RoundingMode.HALF_UP))
                .totalDistanceFare(totalDistanceFare.setScale(2, RoundingMode.HALF_UP))
                .totalTimeFare(totalTimeFare.setScale(2, RoundingMode.HALF_UP))
                .totalSurgeRevenue(totalSurgeRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalPlatformFees(totalPlatformFees.setScale(2, RoundingMode.HALF_UP))
                .revenueChangePercent(revenueChangePercent.setScale(2, RoundingMode.HALF_UP))
                .tripsChangePercent(tripsChangePercent.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public RevenueStatisticsResponse getDailyRevenue(String date) {
        LocalDateTime startOfDay = LocalDateTime.parse(date + "T00:00:00");
        LocalDateTime endOfDay = LocalDateTime.parse(date + "T23:59:59.999999999");
        return getRevenueStatistics(startOfDay, endOfDay);
    }

    public RevenueStatisticsResponse getWeeklyRevenue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime weekEnd = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return getRevenueStatistics(weekStart, weekEnd);
    }

    public RevenueStatisticsResponse getMonthlyRevenue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        return getRevenueStatistics(monthStart, monthEnd);
    }

    public RevenueStatisticsResponse getZoneRevenue(String zoneId, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveStart = startDate != null ? startDate : now.minusDays(7);
        LocalDateTime effectiveEnd = endDate != null ? endDate : now;

        LocalDateTime normalizedStart = effectiveStart.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime normalizedEnd = effectiveEnd.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        log.info("Computing zone revenue statistics for zone {} from {} to {}", zoneId, normalizedStart, normalizedEnd);

        var zoneData = fareEstimateRepository.computeZoneAggregations(normalizedStart, normalizedEnd);

        BigDecimal zoneRevenue = BigDecimal.ZERO;
        Long zoneTrips = 0L;
        BigDecimal avgFare = BigDecimal.ZERO;

        for (var zone : zoneData) {
            if (zoneId.equals(zone.getId())) {
                zoneRevenue = zone.getRevenue() != null ? zone.getRevenue() : BigDecimal.ZERO;
                zoneTrips = zone.getTrips() != null ? zone.getTrips() : 0L;
                avgFare = zone.getAvgFare() != null ? zone.getAvgFare() : BigDecimal.ZERO;
                break;
            }
        }

        return RevenueStatisticsResponse.builder()
                .startDate(normalizedStart)
                .endDate(normalizedEnd)
                .totalRevenue(zoneRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalTrips(zoneTrips)
                .averageFare(avgFare.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public RevenueStatisticsResponse getVehicleTypeRevenue(String vehicleType, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveStart = startDate != null ? startDate : now.minusDays(7);
        LocalDateTime effectiveEnd = endDate != null ? endDate : now;

        LocalDateTime normalizedStart = effectiveStart.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime normalizedEnd = effectiveEnd.withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        log.info("Computing vehicle type revenue statistics for {} from {} to {}", vehicleType, normalizedStart, normalizedEnd);

        var vehicleData = fareEstimateRepository.computeVehicleTypeAggregations(normalizedStart, normalizedEnd);

        BigDecimal vehicleRevenue = BigDecimal.ZERO;
        Long vehicleTrips = 0L;
        BigDecimal avgFare = BigDecimal.ZERO;

        for (var vehicle : vehicleData) {
            if (vehicleType.equals(vehicle.getId())) {
                vehicleRevenue = vehicle.getRevenue() != null ? vehicle.getRevenue() : BigDecimal.ZERO;
                vehicleTrips = vehicle.getTrips() != null ? vehicle.getTrips() : 0L;
                avgFare = vehicle.getAvgFare() != null ? vehicle.getAvgFare() : BigDecimal.ZERO;
                break;
            }
        }

        return RevenueStatisticsResponse.builder()
                .startDate(normalizedStart)
                .endDate(normalizedEnd)
                .totalRevenue(vehicleRevenue.setScale(2, RoundingMode.HALF_UP))
                .totalTrips(vehicleTrips)
                .averageFare(avgFare.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private BigDecimal calculateRevenueChange(LocalDateTime currentStart, LocalDateTime currentEnd) {
        long daysBetween = ChronoUnit.DAYS.between(currentStart, currentEnd);
        LocalDateTime previousStart = currentStart.minusDays(daysBetween + 1);
        LocalDateTime previousEnd = currentStart.minusSeconds(1);

        var previousRevenue = fareEstimateRepository.computeRevenueAggregation(
                previousStart.withHour(0).withMinute(0).withSecond(0),
                previousEnd.withHour(23).withMinute(59).withSecond(59)
        );

        var currentRevenue = fareEstimateRepository.computeRevenueAggregation(currentStart, currentEnd);

        BigDecimal previous = previousRevenue != null && previousRevenue.getTotalRevenue() != null
                ? previousRevenue.getTotalRevenue() : BigDecimal.ZERO;
        BigDecimal current = currentRevenue != null && currentRevenue.getTotalRevenue() != null
                ? currentRevenue.getTotalRevenue() : BigDecimal.ZERO;

        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateTripsChange(LocalDateTime currentStart, LocalDateTime currentEnd) {
        long daysBetween = ChronoUnit.DAYS.between(currentStart, currentEnd);
        LocalDateTime previousStart = currentStart.minusDays(daysBetween + 1);
        LocalDateTime previousEnd = currentStart.minusSeconds(1);

        var previousTrips = fareEstimateRepository.computeRevenueAggregation(
                previousStart.withHour(0).withMinute(0).withSecond(0),
                previousEnd.withHour(23).withMinute(59).withSecond(59)
        );

        var currentTrips = fareEstimateRepository.computeRevenueAggregation(currentStart, currentEnd);

        Long previous = previousTrips != null && previousTrips.getTotalTrips() != null
                ? previousTrips.getTotalTrips() : 0L;
        Long current = currentTrips != null && currentTrips.getTotalTrips() != null
                ? currentTrips.getTotalTrips() : 0L;

        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
