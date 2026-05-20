package iuh.fit.driverservice.dto.response;

import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverCurrentRideResponse {
    String rideId;
    String bookingId;
    String customerId;
    String rideStatus;
    String pickupAddress;
    String destinationAddress;
    DriverLocationPayload pickupLocation;
    DriverLocationPayload destinationLocation;
    String vehicleType;
    String paymentMethod;
    BigDecimal estimatedFare;
    LocalDateTime requestedAt;
    String driverAvailabilityStatus;
    DriverLocationPayload currentLocation;
}
