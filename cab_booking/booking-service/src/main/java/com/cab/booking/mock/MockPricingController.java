package com.cab.booking.mock;

import com.cab.booking.common.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mock Pricing & ETA Service.
 * Giả lập API lấy báo giá và thời gian ước tính từ Pricing Service.
 *
 * FE gọi endpoint này TRƯỚC KHI tạo booking để hiển thị giá cho khách.
 * Response trả về estimatedFare + quoteToken (JWT) để Zero-Trust verify giá.
 */
@RestController
@RequestMapping("/api/mock/pricing")
public class MockPricingController {

    @PostMapping("/estimate")
    public ApiResponse<PriceEstimateResponse> getEstimate(@RequestBody PriceEstimateRequest request) {
        BigDecimal baseFare = calculateMockFare(request.getVehicleType());
        String quoteToken = generateMockQuoteToken(baseFare);

        PriceEstimateResponse response = PriceEstimateResponse.builder()
                .estimatedFare(baseFare)
                .etaMinutes(estimateEta(request.getVehicleType()))
                .vehicleType(request.getVehicleType() != null ? request.getVehicleType() : "SEDAN")
                .quoteToken(quoteToken)
                .expiresAt(Instant.now().plusSeconds(300).toString())
                .build();

        return ApiResponse.success(response);
    }

    private BigDecimal calculateMockFare(String vehicleType) {
        if ("SUV".equals(vehicleType)) return new BigDecimal("85000");
        if ("VAN".equals(vehicleType)) return new BigDecimal("120000");
        if ("BIKE".equals(vehicleType)) return new BigDecimal("25000");
        return new BigDecimal("50000");
    }

    private int estimateEta(String vehicleType) {
        if ("BIKE".equals(vehicleType)) return 3;
        if ("SUV".equals(vehicleType)) return 8;
        if ("VAN".equals(vehicleType)) return 10;
        return 5;
    }

    private String generateMockQuoteToken(BigDecimal fare) {
        String payload = "{\"fare\":" + fare + ",\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}";
        return "MOCK." + java.util.Base64.getEncoder().encodeToString(payload.getBytes()) + ".SIGNATURE";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceEstimateRequest {
        private String pickupLocation;
        private String dropoffLocation;
        private String vehicleType;
        private String promoCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceEstimateResponse {
        private BigDecimal estimatedFare;
        private int etaMinutes;
        private String vehicleType;
        private String quoteToken;
        private String expiresAt;
    }
}
