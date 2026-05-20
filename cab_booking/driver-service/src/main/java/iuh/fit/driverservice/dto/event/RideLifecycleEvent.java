package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideLifecycleEvent {
    private String eventId;
    private String eventType;
    private String type;
    private String rideId;
    private String bookingId;
    private String driverId;
    private String reason;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
