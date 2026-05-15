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
public class RideFinishedEvent {
    public static final String EVENT_TYPE = "RideFinished";

    private String eventId;
    private String type;
    private String rideId;
    private String driverId;
    private BigDecimal finalFare;
    private String paymentMethod;
    private String timestamp;
}
