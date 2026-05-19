package com.cab.matching.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleTypeNormalizerTest {

    @Test
    void normalizesCanonicalValuesCaseInsensitive() {
        assertEquals(VehicleType.BIKE, VehicleTypeNormalizer.normalizeVehicleType("bike"));
        assertEquals(VehicleType.CAR4, VehicleTypeNormalizer.normalizeVehicleType("car4"));
        assertEquals(VehicleType.CAR7, VehicleTypeNormalizer.normalizeVehicleType("CAR7"));
    }

    @Test
    void normalizesLegacyValues() {
        assertEquals(VehicleType.BIKE, VehicleTypeNormalizer.normalizeVehicleType("MOTORCYCLE"));
        assertEquals(VehicleType.CAR4, VehicleTypeNormalizer.normalizeVehicleType("ECONOMY"));
        assertEquals(VehicleType.CAR7, VehicleTypeNormalizer.normalizeVehicleType("CAR_7"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> VehicleTypeNormalizer.normalizeVehicleType("VAN"));
    }
}
