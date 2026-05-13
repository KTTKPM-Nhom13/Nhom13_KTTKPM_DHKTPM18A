package com.cab.matching.client;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Retry độc lập cho {@link AiScoringClient}.
 *
 * <p><b>Cách dùng:</b> Khai báo trong thuộc tính {@code configuration} của {@code @FeignClient}:
 * <pre>
 * {@code
 * @FeignClient(
 *     name        = "ai-scoring",
 *     url         = "${services.ai-scoring.url:http://ai-scoring-service:8000}",
 *     configuration = FeignRetryConfig.class   // <-- GẮN VÀO ĐÂY
 * )
 * public interface AiScoringClient { ... }
 * }
 * </pre>
 *
 * <p><b>Lưu ý scope:</b> Class này KHÔNG được để Spring Boot tự scan làm global config.
 * Thêm package này vào {@code @ComponentScan} exclude nếu cần, hoặc đảm bảo nó
 * chỉ được load thông qua thuộc tính {@code configuration} của {@code @FeignClient}.
 *
 * <p><b>Chiến lược Retry:</b>
 * <ul>
 *   <li>Retry khi gặp {@link feign.RetryableException} (5xx, connection refused).</li>
 *   <li>Lần 1: chờ 100ms trước khi thử lại.</li>
 *   <li>Lần 2: chờ tối đa 1000ms (exponential backoff).</li>
 *   <li>Tổng: tối đa 3 lần thử ({@code maxAttempts = 3}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class FeignRetryConfig {

    /**
     * Cấu hình Retryer của Feign.
     *
     * <p>Tham số của {@link Retryer.Default}:
     * <pre>
     *   new Retryer.Default(period, maxPeriod, maxAttempts)
     *   - period     : khoảng cách giữa các lần retry đầu tiên (ms)
     *   - maxPeriod  : khoảng cách tối đa (ms) - Feign dùng exponential backoff
     *   - maxAttempts: số lần thử tối đa (bao gồm cả lần gọi đầu tiên)
     * </pre>
     *
     * @return Retryer thử 3 lần, bắt đầu từ 100ms, tối đa 1000ms mỗi lần.
     */
    @Bean
    public Retryer aiScoringRetryer() {
        // period=100ms, maxPeriod=1000ms, maxAttempts=3
        // → Lần 1 thất bại: chờ ~100ms → Lần 2 thất bại: chờ ~200ms → Lần 3 thất bại: ném exception
        return new Retryer.Default(100L, 1_000L, 3);
    }
}
