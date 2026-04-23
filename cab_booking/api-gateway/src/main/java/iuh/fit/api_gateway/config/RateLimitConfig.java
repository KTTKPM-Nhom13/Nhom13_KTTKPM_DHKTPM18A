package iuh.fit.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Cấu hình Rate Limiting cho Spring Cloud Gateway.
 * - KeyResolver: giới hạn theo IP client.
 * - ErrorHandler: trả về JSON có ý nghĩa khi bị block (429).
 */
@Configuration
public class RateLimitConfig {

    // ── KeyResolver: giới hạn theo địa chỉ IP của client ──────────────────────

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // Ưu tiên X-Forwarded-For (khi có proxy/load balancer)
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // Lấy IP đầu tiên nếu có nhiều IP (comma-separated)
                String clientIp = forwardedFor.split(",")[0].trim();
                return Mono.just(clientIp);
            }

            // Fallback: lấy từ remote address
            InetSocketAddress remoteAddr = exchange.getRequest().getRemoteAddress();
            if (remoteAddr != null) {
                return Mono.just(remoteAddr.getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }

    // ── KeyResolver thay thế: theo User ID (nếu đã đăng nhập) ──────────────────
    // Bỏ comment và đăng ký bean này nếu muốn giới hạn theo user thay vì IP.

    // @Bean
    // public KeyResolver userKeyResolver() {
    //     return exchange -> Mono.justOrEmpty(
    //             exchange.getRequest().getHeaders().getFirst("X-User-Id")
    //     ).switchIfEmpty(Mono.just("anonymous"));
    // }

    // ── Custom error handler: trả JSON thân thiện khi bị 429 ─────────────────

    @Bean
    public WebExceptionHandler rateLimitExceptionHandler() {
        return (exchange, ex) -> {
            HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;

            if (ex instanceof NotFoundException
                    || (ex instanceof ResponseStatusException rse && rse.getStatusCode() == status)
                    || (ex.getMessage() != null && ex.getMessage().contains("429"))
                    || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("rate limit"))) {

                String body = "{\"error\": \"Too Many Requests\","
                        + " \"message\": \"Ban thao tac qua nhanh. Vui long cho mot chut roi thu lai.\","
                        + " \"retryAfter\": 10}";

                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                exchange.getResponse().getHeaders().put("Retry-After", List.of("10"));
                exchange.getResponse().getHeaders().put("X-RateLimit-Error", List.of("Rate limit exceeded"));

                return exchange.getResponse().writeWith(Mono.just(buffer));
            }
            return Mono.error(ex);
        };
    }
}
