package com.cab.booking.core.enums;

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
        assertEquals(VehicleType.BIKE, VehicleTypeNormalizer.normalizeVehicleType("MOTORBIKE"));
        assertEquals(VehicleType.CAR4, VehicleTypeNormalizer.normalizeVehicleType("SEDAN"));
        assertEquals(VehicleType.CAR7, VehicleTypeNormalizer.normalizeVehicleType("SUV"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> VehicleTypeNormalizer.normalizeVehicleType("TRUCK"));
    }
}
