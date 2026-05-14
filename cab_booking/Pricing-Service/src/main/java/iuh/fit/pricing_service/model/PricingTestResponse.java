package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PricingTestResponse {

    private Double distanceKm;
    
    private Double demandIndex;
    
    private BigDecimal baseFare;
    
    private BigDecimal distanceFare;
    
    private BigDecimal totalFare;
    
    private BigDecimal surgeMultiplier;
    
    private String message;
}
