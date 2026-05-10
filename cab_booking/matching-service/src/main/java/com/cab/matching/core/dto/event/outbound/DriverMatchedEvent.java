package com.cab.matching.core.dto.event.outbound;

public record DriverMatchedEvent(
        String eventType,
        String bookingId,
        String driverId,
        String driverName,
        Double driverLat,
        Double driverLng
) {
    public static DriverMatchedEvent create(String bookingId, String driverId, Double driverLat, Double driverLng) {
        return new DriverMatchedEvent(
                "DRIVER_MATCHED",
                bookingId,
                driverId,
                "Tài xế " + driverId, // Demo tên tạm thời
                driverLat,
                driverLng
        );
    }
}
