package iuh.fit.pricing_service.client;

import iuh.fit.pricing_service.service.SurgePricingService.SurgePredictionRequest;

import java.math.BigDecimal;

public interface SurgePricingAiClient {

    BigDecimal predictSurgeFactor(SurgePredictionRequest request);
}
