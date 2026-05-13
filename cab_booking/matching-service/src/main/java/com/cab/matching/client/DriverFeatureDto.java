package com.cab.matching.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO biểu diễn đặc trưng (feature) của một tài xế ứng viên
 * để gửi lên AI Scoring Service cho bài toán ranking.
 *
 * <p>Phải đồng bộ hoàn toàn với Pydantic model {@code DriverFeature}
 * bên Python / FastAPI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverFeatureDto {

    /** ID duy nhất của tài xế. */
    private String driverId;

    /** Khoảng cách từ tài xế đến điểm đón (km). */
    private double distance;

    /** Thời gian ước tính đến điểm đón (phút). */
    private int eta;

    /** Điểm đánh giá trung bình của tài xế (0.0 – 5.0). */
    private double rating;

    /**
     * Hệ số giá cước hiện tại của tài xế (surge pricing).
     * 1.0 = giá bình thường, 1.5 = giá x1.5 (đắt hơn).
     * AI model sẽ ưu tiên tài xế có priceMultiplier thấp hơn.
     */
    private double priceMultiplier;
}

