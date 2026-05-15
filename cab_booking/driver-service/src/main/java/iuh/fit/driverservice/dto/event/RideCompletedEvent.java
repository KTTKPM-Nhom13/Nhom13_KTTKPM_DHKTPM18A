package iuh.fit.driverservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCompletedEvent {
    public static final String EVENT_TYPE = "RideCompleted";

    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private BigDecimal finalFare;
    private String paymentMethod;
    private String timestamp;
}
