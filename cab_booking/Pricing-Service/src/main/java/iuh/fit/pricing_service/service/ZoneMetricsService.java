package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.client.BookingServiceClient;
import iuh.fit.pricing_service.client.DriverServiceClient;
import iuh.fit.pricing_service.model.SurgeRule;
import iuh.fit.pricing_service.repository.SurgeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneMetricsService {

    private final DriverServiceClient driverServiceClient;
    private final BookingServiceClient bookingServiceClient;
    private final SurgeRuleRepository surgeRuleRepository;
    private final SurgePricingService surgePricingService;

    @Value("${services.zone-metrics.default-radius-km:2.0}")
    private double defaultRadiusKm;

    public void syncMetricsForZone(String zoneId) {
        surgeRuleRepository.findByZoneId(zoneId).ifPresentOrElse(
                rule -> syncMetricsForRule(rule),
                () -> log.debug("No surge rule found for zone {}, skipping sync", zoneId)
        );
    }

    public void syncMetricsForRule(SurgeRule rule) {
        String zoneId = rule.getZoneId();
        if (rule.getLatitude() == null || rule.getLongitude() == null) {
            log.warn("Cannot sync metrics for zone {}: latitude or longitude is null", zoneId);
            return;
        }
        double lat = rule.getLatitude().doubleValue();
        double lng = rule.getLongitude().doubleValue();
        double radius = rule.getRadiusKm() != null ? rule.getRadiusKm() : defaultRadiusKm;

        log.info("Syncing zone metrics for zone {} (lat={}, lng={}, radius={}km)",
                zoneId, lat, lng, radius);

        int activeDrivers = countActiveDriversInArea(lat, lng, radius);
        int pendingRides = countPendingRidesInArea(lat, lng, radius);

        surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
        log.info("Zone {} metrics updated: activeDrivers={}, pendingRides={}",
                zoneId, activeDrivers, pendingRides);
    }

    public void syncAllActiveZones() {
        List<SurgeRule> allRules = surgeRuleRepository.findAll();
        log.info("Starting scheduled sync for {} surge rule zones", allRules.size());

        for (SurgeRule rule : allRules) {
            try {
                syncMetricsForRule(rule);
            } catch (Exception e) {
                log.error("Failed to sync metrics for zone {}: {}", rule.getZoneId(), e.getMessage());
            }
        }

        log.info("Scheduled sync completed for {} zones", allRules.size());
    }

    public int countActiveDriversInArea(double lat, double lng, double radiusKm) {
        try {
            List<DriverServiceClient.DriverProfileDto> allDrivers = driverServiceClient.getAllDrivers();
            if (allDrivers == null) {
                log.warn("Driver service returned null list");
                return 0;
            }

            int count = 0;
            for (DriverServiceClient.DriverProfileDto driver : allDrivers) {
                if (isDriverActiveAndInArea(driver, lat, lng, radiusKm)) {
                    count++;
                }
            }
            log.debug("Found {} active drivers in area (lat={}, lng={}, radius={}km)",
                    count, lat, lng, radiusKm);
            return count;
        } catch (Exception e) {
            log.warn("Failed to fetch drivers from driver-service: {}", e.getMessage());
            return 0;
        }
    }

    public int countPendingRidesInArea(double lat, double lng, double radiusKm) {
        try {
            List<BookingServiceClient.BookingResponseDto> nearbyBookings =
                    bookingServiceClient.getNearbyBookings(lat, lng, radiusKm);
            if (nearbyBookings == null) {
                log.warn("Booking service returned null list");
                return 0;
            }

            int count = 0;
            for (BookingServiceClient.BookingResponseDto booking : nearbyBookings) {
                if ("MATCHING".equalsIgnoreCase(booking.status())) {
                    count++;
                }
            }
            log.debug("Found {} pending rides in area (lat={}, lng={}, radius={}km)",
                    count, lat, lng, radiusKm);
            return count;
        } catch (Exception e) {
            log.warn("Failed to fetch bookings from booking-service: {}", e.getMessage());
            return 0;
        }
    }

    public Map<String, ZoneMetricsSummary> getMetricsSummary() {
        Map<String, ZoneMetricsSummary> summary = new HashMap<>();
        List<SurgeRule> allRules = surgeRuleRepository.findAll();

        for (SurgeRule rule : allRules) {
            String zoneId = rule.getZoneId();
            try {
                var redisMetrics = surgePricingService.getCurrentZoneMetrics(zoneId);
                if (redisMetrics.isPresent()) {
                    var m = redisMetrics.get();
                    summary.put(zoneId, new ZoneMetricsSummary(
                            zoneId,
                            rule.getZoneName(),
                            m.activeDrivers(),
                            m.pendingRides(),
                            m.updatedAt().toString()
                    ));
                } else {
                    summary.put(zoneId, new ZoneMetricsSummary(
                            zoneId,
                            rule.getZoneName(),
                            0,
                            0,
                            null
                    ));
                }
            } catch (Exception e) {
                log.warn("Failed to get metrics summary for zone {}: {}", zoneId, e.getMessage());
                summary.put(zoneId, new ZoneMetricsSummary(zoneId, rule.getZoneName(), 0, 0, null));
            }
        }
        return summary;
    }

    private boolean isDriverActiveAndInArea(
            DriverServiceClient.DriverProfileDto driver,
            double centerLat,
            double centerLng,
            double radiusKm) {
        if (driver.availabilityStatus() == null || driver.currentLatitude() == null || driver.currentLongitude() == null) {
            return false;
        }
        if (!"ONLINE".equalsIgnoreCase(driver.availabilityStatus())) {
            return false;
        }
        if (!"APPROVED".equalsIgnoreCase(driver.verificationStatus())) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(driver.accountStatus())) {
            return false;
        }
        return isWithinRadius(driver.currentLatitude(), driver.currentLongitude(), centerLat, centerLng, radiusKm);
    }

    private boolean isWithinRadius(double lat1, double lng1, double lat2, double lng2, double radiusKm) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = 6371.0 * c;
        return distance <= radiusKm;
    }

    public record ZoneMetricsSummary(
            String zoneId,
            String zoneName,
            int activeDrivers,
            int pendingRides,
            String updatedAt
    ) {}
}
