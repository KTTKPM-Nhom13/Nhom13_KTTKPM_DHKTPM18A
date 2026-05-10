package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FareEstimateResponse {

    private String estimateId;

    private String pickupZone;

    private String dropoffZone;

    private String vehicleType;

    private Double distanceKm;

    private Integer durationMinutes;

    private BigDecimal baseFare;

    private BigDecimal distanceFare;

    private BigDecimal timeFare;

    private BigDecimal surgeMultiplier;

    private BigDecimal totalFare;

    private String currency;

    private LocalDateTime expiresAt;

    private String message;

    public static FareEstimateResponse fromFareEstimate(FareEstimate estimate) {
        return FareEstimateResponse.builder()
                .estimateId(estimate.getId())
                .pickupZone(estimate.getPickupZone())
                .dropoffZone(estimate.getDropoffZone())
                .vehicleType(estimate.getVehicleType())
                .distanceKm(estimate.getDistanceKm())
                .durationMinutes(estimate.getDurationMinutes())
                .baseFare(estimate.getBaseFare())
                .distanceFare(estimate.getDistanceFare())
                .timeFare(estimate.getTimeFare())
                .surgeMultiplier(estimate.getSurgeMultiplier())
                .totalFare(estimate.getTotalFare())
                .currency(estimate.getCurrency())
                .expiresAt(estimate.getExpiresAt())
                .message("Fare estimate generated successfully")
                .build();
    }
}
