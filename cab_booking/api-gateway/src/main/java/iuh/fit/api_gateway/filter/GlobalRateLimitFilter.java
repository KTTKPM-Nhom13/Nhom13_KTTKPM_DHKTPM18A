package iuh.fit.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class GlobalRateLimitFilter implements GlobalFilter, WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalRateLimitFilter.class);

    private static final int ETA_BURST_CAPACITY = 3;
    private static final int AUTH_BURST_CAPACITY = 1;
    private static final int WINDOW_SECONDS = 1;
    private static final String RATE_LIMIT_PROCESSED_ATTR = "rate_limit_processed";

    // Ý nghĩa:

    // Mỗi IP+
    // mỗi prefix
    // path có
    // một bộ
    // đếm riêng.Trong 1
    // cửa sổ 1 giây,
    // ETA cho
    // phép tối đa 3 request,
    // Auth tối đa 1
    // request.
    // Vượt ngưỡng
    // thì trả 429.
    // Cơ chế refresh cửa sổ 1 giây
    // dùng fixed window bằng epoch second:
    // Nếu request tới lúc 10:00:00.xxx thì cùng bucket giây 10:00:00
    // Qua 10:00:01.000 thì sang bucket mới, bộ đếm reset logic theo key mới

    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private StringRedisTemplate syncRedisTemplate;

    public GlobalRateLimitFilter() {
        log.info("GlobalRateLimitFilter created. Sync Redis: {}",
                syncRedisTemplate != null ? "available" : "not available");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return applyRateLimit(exchange, () -> chain.filter(exchange), "GatewayFilter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return applyRateLimit(exchange, () -> chain.filter(exchange), "WebFilter");
    }

    // 1: Gateway kiểm tra limit: filter chính
    private Mono<Void> applyRateLimit(ServerWebExchange exchange, Supplier<Mono<Void>> next, String source) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        if (exchange.getAttribute(RATE_LIMIT_PROCESSED_ATTR) != null) {
            return next.get();
        }
        exchange.getAttributes().put(RATE_LIMIT_PROCESSED_ATTR, Boolean.TRUE);

        log.info("GlobalRateLimitFilter invoked ({}) : {} {}", source, method, path);

        if ("OPTIONS".equals(method)) {
            return next.get();
        }

        String clientIp = getClientIp(exchange);
        RateLimitConfig config = getConfigForPath(path);

        log.debug("FILTER CALLED: {} {} from {}", method, path, clientIp);

        if (syncRedisTemplate != null) {
            return checkRedisRateLimit(exchange, next, clientIp, config);
        }
        return checkInMemoryRateLimit(exchange, next, clientIp, config);
    }

    private Mono<Void> checkInMemoryRateLimit(ServerWebExchange exchange, Supplier<Mono<Void>> next,
            String clientIp, RateLimitConfig config) {
        long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        String key = clientIp + ":" + config.prefix + ":" + window;

        SlidingWindow sw = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.window != window) {
                return new SlidingWindow(window);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int count = sw.count.get();
        log.debug("In-memory count: {} / {}", count, config.burstCapacity);

        if (count > config.burstCapacity) {
            log.warn("RATE LIMIT EXCEEDED: {} > {} for {}", count, config.burstCapacity, key);
            return write429(exchange, WINDOW_SECONDS);
        }

        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, config.burstCapacity - count)));
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(config.burstCapacity));

        return next.get();
    }

    private Mono<Void> checkRedisRateLimit(ServerWebExchange exchange, Supplier<Mono<Void>> next,
            String clientIp, RateLimitConfig config) {
        long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        String key = "rate:" + clientIp + ":" + config.prefix + ":" + window;

        return Mono.fromCallable(() -> {
            Long count = syncRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                syncRedisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS + 2));
            }
            return count != null ? count : 0L;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(count -> {
                    if (count > config.burstCapacity) {
                        log.warn("RATE LIMIT EXCEEDED (Redis): {} > {} for {}", count, config.burstCapacity, key);
                        return write429(exchange, WINDOW_SECONDS);
                    }
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                            String.valueOf(Math.max(0, config.burstCapacity - count)));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(config.burstCapacity));
                    return next.get();
                })
                .onErrorResume(e -> {
                    log.error("Redis rate limit error, falling back: {}", e.getMessage());
                    return checkInMemoryRateLimit(exchange, next, clientIp, config);
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "anonymous";
    }

    // 2: Map path sang cấu hình ETA/Auth
    private RateLimitConfig getConfigForPath(String path) {
        if (path.startsWith("/eta/") || path.startsWith("/api/eta/"))
            return new RateLimitConfig("eta", ETA_BURST_CAPACITY);
        if (path.startsWith("/auth/") || path.startsWith("/api/auth/"))
            return new RateLimitConfig("auth", AUTH_BURST_CAPACITY);
        return new RateLimitConfig("default", 1000);
    }

    // 3: Nếu vượt ngưỡng thì trả 429
    private Mono<Void> write429(ServerWebExchange exchange, int retrySeconds) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().put("Retry-After", List.of(String.valueOf(retrySeconds)));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");

        String body = String.format(
                "{\"error\":\"Too Many Requests\",\"message\":\"Ban thao tac qua nhanh. Vui long cho %ds roi thu lai.\",\"retryAfter\":%d}",
                retrySeconds, retrySeconds);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    private record RateLimitConfig(String prefix, int burstCapacity) {
    }

    private static class SlidingWindow {
        final AtomicInteger count;
        final long window;

        SlidingWindow(long window) {
            this.window = window;
            this.count = new AtomicInteger(1);
        }
    }
}
