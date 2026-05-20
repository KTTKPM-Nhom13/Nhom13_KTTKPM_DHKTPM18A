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
    private String eventId;
    private String type;
    private String eventType;
    private String rideId;
    private String bookingId;
    private String customerId;
    private String driverId;
    private BigDecimal finalFare;
    private BigDecimal amount;
    private String currency;
    private String gatewayTransactionId;
    private String paymentMethod;
    private String timestamp;

    public String aggregateId() {
        return rideId != null && !rideId.isBlank() ? rideId : bookingId;
    }
}
