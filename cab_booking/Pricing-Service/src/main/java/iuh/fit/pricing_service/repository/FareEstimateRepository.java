package iuh.fit.pricing_service.repository;

import iuh.fit.pricing_service.model.FareEstimate;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FareEstimateRepository extends MongoRepository<FareEstimate, String> {

    Optional<FareEstimate> findByRideId(String rideId);

    List<FareEstimate> findByStatus(String status);

    List<FareEstimate> findByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);

    List<FareEstimate> findByVehicleType(String vehicleType);

    List<FareEstimate> findByPickupZone(String pickupZone);

    List<FareEstimate> findByDropoffZone(String dropoffZone);

    List<FareEstimate> findByVehicleTypeAndStatus(String vehicleType, String status);

    List<FareEstimate> findByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<FareEstimate> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: null, totalRevenue: { $sum: '$totalFare' }, totalTrips: { $sum: 1 }, avgFare: { $avg: '$totalFare' } } }"
    })
    RevenueAggregation computeRevenueAggregation(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, revenue: { $sum: '$totalFare' }, trips: { $sum: 1 }, avgFare: { $avg: '$totalFare' } } }",
            "{ $sort: { _id: 1 } }"
    })
    List<DailyAggregation> computeDailyAggregations(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: '$pickupZone', revenue: { $sum: '$totalFare' }, trips: { $sum: 1 }, avgFare: { $avg: '$totalFare' } } }",
            "{ $sort: { revenue: -1 } }"
    })
    List<ZoneAggregation> computeZoneAggregations(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: '$vehicleType', revenue: { $sum: '$totalFare' }, trips: { $sum: 1 }, avgFare: { $avg: '$totalFare' } } }",
            "{ $sort: { revenue: -1 } }"
    })
    List<VehicleTypeAggregation> computeVehicleTypeAggregations(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 } } }",
            "{ $group: { _id: null, totalBaseFare: { $sum: '$baseFare' }, totalDistanceFare: { $sum: '$distanceFare' }, totalTimeFare: { $sum: '$timeFare' }, totalSurge: { $sum: { $subtract: ['$totalFare', '$baseFare'] } }, totalPlatformFees: { $sum: '$platformFee' } } }"
    })
    FareComponentAggregation computeFareComponents(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
            "{ $match: { status: 'CONFIRMED', createdAt: { $gte: ?0, $lte: ?1 }, createdAt: { $gte: ?2, $lt: ?3 } } }",
            "{ $group: { _id: null, revenue: { $sum: '$totalFare' }, trips: { $sum: 1 } } }"
    })
    RevenueAggregation computeRevenueAggregationForPeriod(LocalDateTime start, LocalDateTime end, LocalDateTime periodStart, LocalDateTime periodEnd);

    interface RevenueAggregation {
        BigDecimal getTotalRevenue();
        Long getTotalTrips();
        BigDecimal getAvgFare();
    }

    interface DailyAggregation {
        String getId();
        BigDecimal getRevenue();
        Long getTrips();
        BigDecimal getAvgFare();
    }

    interface ZoneAggregation {
        String getId();
        BigDecimal getRevenue();
        Long getTrips();
        BigDecimal getAvgFare();
    }

    interface VehicleTypeAggregation {
        String getId();
        BigDecimal getRevenue();
        Long getTrips();
        BigDecimal getAvgFare();
    }

    interface FareComponentAggregation {
        BigDecimal getTotalBaseFare();
        BigDecimal getTotalDistanceFare();
        BigDecimal getTotalTimeFare();
        BigDecimal getTotalSurge();
        BigDecimal getTotalPlatformFees();
    }
}
