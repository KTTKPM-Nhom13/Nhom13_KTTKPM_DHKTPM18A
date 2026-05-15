package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideAcceptRequestedEvent {
    public static final String EVENT_TYPE = "RideAcceptRequested";

    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private String timestamp;
}
