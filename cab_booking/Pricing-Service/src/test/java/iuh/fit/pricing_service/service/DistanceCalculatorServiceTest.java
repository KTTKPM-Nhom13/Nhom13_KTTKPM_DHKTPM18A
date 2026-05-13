package iuh.fit.pricing_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceCalculatorServiceTest {

    private DistanceCalculatorService distanceCalculatorService;

    @BeforeEach
    void setUp() {
        distanceCalculatorService = new DistanceCalculatorService();
    }

    @Test
    @DisplayName("Should calculate distance correctly between two points")
    void testCalculateDistance() {
        double pickupLat = 10.7629;
        double pickupLng = 106.6828;
        double dropoffLat = 10.8231;
        double dropoffLng = 106.6294;

        double distance = distanceCalculatorService.calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);

        assertTrue(distance > 0, "Distance should be positive");
        assertTrue(distance < 20, "Distance in Ho Chi Minh City should be less than 20 km");
    }

    @Test
    @DisplayName("Should return zero distance for same coordinates")
    void testCalculateDistanceSamePoint() {
        double lat = 10.7629;
        double lng = 106.6828;

        double distance = distanceCalculatorService.calculateDistance(lat, lng, lat, lng);

        assertEquals(0.0, distance, 0.001, "Distance should be zero for same coordinates");
    }

    @Test
    @DisplayName("Should calculate distance between Ho Chi Minh City and Hanoi")
    void testCalculateLongDistance() {
        double hcmcLat = 10.7629;
        double hcmcLng = 106.6828;
        double hanoiLat = 21.0285;
        double hanoiLng = 105.8542;

        double distance = distanceCalculatorService.calculateDistance(hcmcLat, hcmcLng, hanoiLat, hanoiLng);

        assertTrue(distance > 1100, "Distance between HCMC and Hanoi should be over 1100 km");
        assertTrue(distance < 1800, "Distance between HCMC and Hanoi should be less than 1800 km");
    }

    @Test
    @DisplayName("Should estimate duration correctly")
    void testEstimateDurationMinutes() {
        double distanceKm = 10.0;
        double averageSpeedKmh = 30.0;

        int duration = distanceCalculatorService.estimateDurationMinutes(distanceKm, averageSpeedKmh);

        assertEquals(20, duration, "10 km at 30 km/h should take 20 minutes");
    }

    @Test
    @DisplayName("Should handle zero speed gracefully")
    void testEstimateDurationWithZeroSpeed() {
        double distanceKm = 10.0;
        double averageSpeedKmh = 0;

        int duration = distanceCalculatorService.estimateDurationMinutes(distanceKm, averageSpeedKmh);

        assertTrue(duration > 0, "Duration should be at least 1 minute even with zero speed");
    }

    @Test
    @DisplayName("Should estimate duration by distance using default speed")
    void testEstimateDurationMinutesByDistance() {
        double distanceKm = 15.0;

        int duration = distanceCalculatorService.estimateDurationMinutesByDistance(distanceKm);

        assertTrue(duration > 0, "Duration should be positive");
    }

    @Test
    @DisplayName("Should round distance to two decimal places")
    void testCalculateDistanceRounding() {
        double lat1 = 10.7629;
        double lng1 = 106.6828;
        double lat2 = 10.8231;
        double lng2 = 106.6294;

        double distance = distanceCalculatorService.calculateDistance(lat1, lng1, lat2, lng2);

        double roundedValue = Math.round(distance * 100.0) / 100.0;
        assertEquals(roundedValue, distance, "Distance should be rounded to 2 decimal places");
    }
}
