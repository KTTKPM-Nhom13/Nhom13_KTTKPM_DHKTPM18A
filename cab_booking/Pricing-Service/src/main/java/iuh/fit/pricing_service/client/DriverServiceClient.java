package iuh.fit.pricing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "driver-service")
public interface DriverServiceClient {

    @GetMapping("/api/admin/drivers")
    List<DriverProfileDto> getAllDrivers();

    @GetMapping("/api/drivers/me/availability")
    DriverAvailabilityDto getDriverAvailability(@RequestParam String driverId);

    record DriverProfileDto(
            String id,
            String externalUserId,
            String fullName,
            String phoneNumber,
            String vehicleType,
            String availabilityStatus,
            String verificationStatus,
            String accountStatus,
            Double currentLatitude,
            Double currentLongitude
    ) {}

    record DriverAvailabilityDto(
            String externalUserId,
            String availabilityStatus,
            boolean online,
            boolean offline,
            boolean activeForBooking,
            String verificationStatus,
            String currentRideId,
            String currentRideStatus,
            Double currentLatitude,
            Double currentLongitude
    ) {}
}
