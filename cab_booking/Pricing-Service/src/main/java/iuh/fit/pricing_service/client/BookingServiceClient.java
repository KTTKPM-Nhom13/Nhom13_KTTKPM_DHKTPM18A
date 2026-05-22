package iuh.fit.pricing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "booking-service")
public interface BookingServiceClient {

    @GetMapping("/api/v1/bookings/nearby")
    List<BookingResponseDto> getNearbyBookings(
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude,
            @RequestParam(value = "radius", defaultValue = "5.0") double radiusKm
    );

    record BookingResponseDto(
            String id,
            String customerId,
            String assignedDriverId,
            String pickupLocation,
            String dropoffLocation,
            String vehicleType,
            String paymentMethod,
            String status,
            PickupCoordinatesDto pickupCoordinates,
            DropoffCoordinatesDto dropoffCoordinates
    ) {}

    record PickupCoordinatesDto(double lat, double lng) {}

    record DropoffCoordinatesDto(double lat, double lng) {}
}
