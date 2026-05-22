package iuh.fit.pricing_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneServiceTest {

    private final ZoneService zoneService = new ZoneService();

    @Test
    void determineZoneCreatesDifferentZonesForDifferentCityLocations() {
        String districtOneZone = zoneService.determineZone(10.8231, 106.6297);
        String airportZone = zoneService.determineZone(10.8169828, 106.6565808);

        assertThat(districtOneZone).isNotEqualTo(airportZone);
    }

    @Test
    void determineZoneKeepsNearbyCoordinatesInTheSameGridCell() {
        String first = zoneService.determineZone(10.8231, 106.6297);
        String second = zoneService.determineZone(10.82319, 106.62979);

        assertThat(first).isEqualTo(second);
    }
}
