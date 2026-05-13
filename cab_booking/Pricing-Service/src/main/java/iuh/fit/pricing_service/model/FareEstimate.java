package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "fare_estimates")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FareEstimate {

    @Id
    private String id;

    private String rideId;

    private String pickupZone;

    private String dropoffZone;

    private Double pickupLat;

    private Double pickupLng;

    private Double dropoffLat;

    private Double dropoffLng;

    private String vehicleType;

    private Double distanceKm;

    private Integer durationMinutes;

    private BigDecimal baseFare;

    private BigDecimal distanceFare;

    private BigDecimal timeFare;

    private BigDecimal surgeMultiplier;

    private BigDecimal totalFare;

    private String currency;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private String schemaVersion;

    public enum VehicleType {
        ECONOMY,
        COMFORT,
        PREMIUM
    }

    public enum EstimateStatus {
        PENDING,
        CONFIRMED,
        EXPIRED,
        CANCELLED
    }
}
