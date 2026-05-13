package com.cab.matching.client;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

/**
 * Cấu hình Feign dành riêng cho {@link AiScoringClient}.
 *
 * <p><b>Scope:</b> Class này KHÔNG được annotate {@code @Configuration} ở mức global
 * để tránh bị Spring Boot auto-scan áp dụng cho tất cả Feign clients khác.
 * Nó chỉ được load khi được khai báo trong thuộc tính {@code configuration}
 * của {@code @FeignClient}.
 *
 * <h2>Chiến lược xử lý lỗi:</h2>
 * <ul>
 *   <li><b>4xx (Client Error)</b>: Ném {@code AiScoringException} — lỗi do dữ liệu đầu vào,
 *       không nên retry.</li>
 *   <li><b>5xx (Server Error / Python service sập)</b>: Ném {@code RetryableException} →
 *       Feign sẽ retry tự động theo cấu hình {@code Retryer} bên dưới.</li>
 *   <li><b>Timeout</b>: Được xử lý bởi {@code Request.Options} (connect/read timeout).</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class FeignConfig {

    /**
     * ErrorDecoder: phân loại lỗi HTTP từ AI Scoring Service.
     */
    @Bean
    public ErrorDecoder aiScoringErrorDecoder() {
        return new AiScoringErrorDecoder();
    }

    /**
     * Retryer: retry tối đa 3 lần khi gặp lỗi 5xx hoặc connection refused.
     * Interval: 500ms → 1s → 2s (exponential backoff, max 2s).
     */
    @Bean
    public feign.Retryer aiScoringRetryer() {
        return new feign.Retryer.Default(500L, 2000L, 3);
    }

    /**
     * Request options: timeout cho từng request đến Python service.
     * connectTimeout: 2s — thời gian chờ thiết lập kết nối TCP.
     * readTimeout: 5s — thời gian chờ phản hồi (model inference có thể chậm).
     */
    @Bean
    public feign.Request.Options aiScoringRequestOptions() {
        return new feign.Request.Options(
                2_000,  java.util.concurrent.TimeUnit.MILLISECONDS,  // connect timeout
                5_000,  java.util.concurrent.TimeUnit.MILLISECONDS,  // read timeout
                true    // followRedirects
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Inner ErrorDecoder implementation
    // ─────────────────────────────────────────────────────────────

    static class AiScoringErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            int status = response.status();
            String message = "ai-scoring-service responded with HTTP " + status
                    + " | method=" + methodKey;

            if (status >= 500) {
                // Service Python bị sập hoặc internal error → cho phép retry
                log.error("🔴 AI Scoring Service lỗi server: {}", message);
                return new RetryableException(
                        status,
                        message,
                        response.request().httpMethod(),
                        new Date(),        // retryAfter — retry ngay
                        response.request()
                );
            }

            if (status == 422) {
                // FastAPI Unprocessable Entity — dữ liệu đầu vào không hợp lệ
                log.warn("⚠️ AI Scoring: dữ liệu không hợp lệ (422): {}", message);
                return new AiScoringException("Dữ liệu gửi lên AI model không hợp lệ: " + message);
            }

            // 4xx khác: không retry, ném exception nghiệp vụ
            log.warn("⚠️ AI Scoring client error: {}", message);
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
