package iuh.fit.driverservice.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum VehicleType {
    BIKE,
    CAR4,
    CAR7;

    @JsonCreator
    public static VehicleType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return VehicleType.valueOf(value.trim().toUpperCase());
    }
}
