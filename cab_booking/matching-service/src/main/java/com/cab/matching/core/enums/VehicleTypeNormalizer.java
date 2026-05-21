package com.cab.matching.core.enums;

public final class VehicleTypeNormalizer {

    private VehicleTypeNormalizer() {
    }

    public static VehicleType normalizeVehicleType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("vehicleType is required");
        }

        return switch (raw.trim().toUpperCase()) {
            case "BIKE", "MOTORBIKE", "MOTORCYCLE" -> VehicleType.BIKE;
            case "CAR", "CAR4", "CAR_4", "SEDAN", "ECONOMY" -> VehicleType.CAR4;
            case "CAR7", "CAR_7", "SUV" -> VehicleType.CAR7;
            default -> throw new IllegalArgumentException("Unsupported vehicleType: " + raw);
        };
    }
}
