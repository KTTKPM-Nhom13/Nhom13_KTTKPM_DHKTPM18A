package iuh.fit.driverservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandleDriverAssignmentRequest {
    @NotBlank
    @Size(max = 100)
    String rideId;

    @NotBlank
    @Size(max = 20)
    String action;

    @Size(max = 255)
    String pickupAddress;

    @Size(max = 255)
    String destinationAddress;

    LocalDateTime requestedAt;
}
