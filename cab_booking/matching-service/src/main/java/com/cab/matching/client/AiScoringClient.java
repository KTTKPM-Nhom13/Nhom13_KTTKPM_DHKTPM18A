package com.cab.matching.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Feign Client giao tiếp với AI Scoring Service (Python / FastAPI).
 *
 * <p>URL được resolve theo thứ tự ưu tiên:
 * <ol>
 *   <li>Biến môi trường / application.yaml: {@code services.ai-scoring.url}</li>
 *   <li>Default: {@code http://ai-scoring-service:8000} (Docker network)</li>
 * </ol>
 *
 * <p>ErrorDecoder và timeout được cấu hình trong {@link FeignConfig}.
 */
@FeignClient(
        name = "ai-scoring",
        url = "${services.ai-scoring.url:http://ai-scoring-service:8000}",
        configuration = FeignConfig.class
)
public interface AiScoringClient {

    /**
     * Gửi danh sách tài xế ứng viên lên AI model để nhận kết quả xếp hạng.
     *
     * @param candidates danh sách feature của từng tài xế
     * @return kết quả scoring: bestDriverId + full ranking
     */
    @PostMapping("/api/v1/ai/score")
    AiScoringResponse getBestMatch(@RequestBody List<DriverFeatureDto> candidates);
}
