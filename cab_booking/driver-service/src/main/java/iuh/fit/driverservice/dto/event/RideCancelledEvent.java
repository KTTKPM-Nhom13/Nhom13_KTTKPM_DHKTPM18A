package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCancelledEvent {
    private String eventId;
    private String eventType;
    private String type;
    private String rideId;
    private String bookingId;
    private String customerId;
    private String driverId;
    private String reason;
    private String timestamp;
}
