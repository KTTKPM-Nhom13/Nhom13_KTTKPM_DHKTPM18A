package iuh.fit.pricing_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DistanceCalculatorService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public double calculateDistance(double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        double dLat = Math.toRadians(dropoffLat - pickupLat);
        double dLng = Math.toRadians(dropoffLng - pickupLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(dropoffLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS_KM * c;
        log.debug("Calculated distance between ({}, {}) and ({}, {}): {} km",
                pickupLat, pickupLng, dropoffLat, dropoffLng, distance);

        return Math.round(distance * 100.0) / 100.0;
    }

    public int estimateDurationMinutes(double distanceKm, double averageSpeedKmh) {
        if (averageSpeedKmh <= 0) {
            averageSpeedKmh = 30.0;
        }
        int duration = (int) Math.ceil((distanceKm / averageSpeedKmh) * 60);
        log.debug("Estimated duration for {} km at {} km/h: {} minutes", distanceKm, averageSpeedKmh, duration);
        return Math.max(duration, 1);
    }

    public int estimateDurationMinutesByDistance(double distanceKm) {
        double averageSpeedKmh = 30.0;
        return estimateDurationMinutes(distanceKm, averageSpeedKmh);
    }
}
