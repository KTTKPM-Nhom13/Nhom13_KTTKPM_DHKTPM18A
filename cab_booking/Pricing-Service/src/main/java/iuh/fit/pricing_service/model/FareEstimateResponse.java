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

    private BigDecimal platformFee;

    private BigDecimal zoneFee;

    private BigDecimal airportFee;

    private BigDecimal tollFee;

    private BigDecimal discountAmount;

    private BigDecimal surgeMultiplier;

    private BigDecimal totalFare;

    private String currency;

    private String pricingConfigVersion;

    private String distanceSource;

    private String weatherCondition;

    private String weatherSource;

    private Boolean fallbackUsed;

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
                .platformFee(estimate.getPlatformFee())
                .zoneFee(estimate.getZoneFee())
                .airportFee(estimate.getAirportFee())
                .tollFee(estimate.getTollFee())
                .discountAmount(estimate.getDiscountAmount())
                .surgeMultiplier(estimate.getSurgeMultiplier())
                .totalFare(estimate.getTotalFare())
                .currency(estimate.getCurrency())
                .pricingConfigVersion(estimate.getPricingConfigVersion())
                .distanceSource(estimate.getDistanceSource())
                .weatherCondition(estimate.getWeatherCondition())
                .weatherSource(estimate.getWeatherSource())
                .fallbackUsed(estimate.getFallbackUsed())
                .expiresAt(estimate.getExpiresAt())
                .message("Fare estimate generated successfully")
                .build();
    }
}
