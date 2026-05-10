package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusEvent {

    public static final String EVENT_TYPE = "DriverStatusChanged";

    private String eventId;
    private String type;
    private String driverId;
    private String availabilityStatus;
    private Boolean activeForBooking;
    private String rideId;
    private String rideStatus;
    private DriverLocationPayload currentLocation;
    private String timestamp;
}
