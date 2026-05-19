package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAcceptedEvent {
    public static final String EVENT_TYPE = "DRIVER_ACCEPTED";

    private String eventId;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String timestamp;
}
