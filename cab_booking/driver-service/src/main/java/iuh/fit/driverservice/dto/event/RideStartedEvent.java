package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideStartedEvent {
    public static final String EVENT_TYPE = "RideStarted";

    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private String timestamp;
}
