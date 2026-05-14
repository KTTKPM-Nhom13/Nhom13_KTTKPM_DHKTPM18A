package com.cab.matching.core.dto.event.outbound;

public record DriverMatchedEvent(
        String eventType,
        String rideId,
        String driverId,
        String driverName,
        Double driverLat,
        Double driverLng
) {
    public static DriverMatchedEvent create(String rideId, String driverId, Double driverLat, Double driverLng) {
        return new DriverMatchedEvent(
                "DRIVER_MATCHED",
                rideId,
                driverId,
                "Tài xế " + driverId, // Demo tên tạm thời
                driverLat,
                driverLng
        );
    }
}
