package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRejectRequestedEvent {
    public static final String EVENT_TYPE = "RideRejectRequested";

    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private String reason;
    private String timestamp;
}
