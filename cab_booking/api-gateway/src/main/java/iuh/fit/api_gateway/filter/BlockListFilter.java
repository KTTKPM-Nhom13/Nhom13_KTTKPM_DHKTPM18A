package iuh.fit.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class BlockListFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(BlockListFilter.class);
    private static final String BLOCKED_USERS_KEY = "blocked_user_ids";

    @Autowired(required = false)
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                        String userId = jwt.getSubject();
                        
                        // Check if it's an exception path that should be allowed even if blocked
                        if (isAllowedLifecycleAction(path, method)) {
                            return chain.filter(exchange);
                        }

                        if (reactiveRedisTemplate != null) {
                            return reactiveRedisTemplate.opsForSet().isMember(BLOCKED_USERS_KEY, userId)
                                    .flatMap(isBlocked -> {
                                        if (Boolean.TRUE.equals(isBlocked)) {
                                            log.warn("Blocked user attempt (Path: {}): {}", path, userId);
                                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                            return exchange.getResponse().setComplete();
                                        }
                                        return chain.filter(exchange);
                                    });
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private boolean isAllowedLifecycleAction(String path, String method) {
        // Check if the request is related to ride or booking status/lifecycle
        boolean isRideRelated = path.contains("/rides") || path.contains("/bookings");

        // allow GET requests to see ride info so they can finish their trip
        if ("GET".equalsIgnoreCase(method) && isRideRelated) {
            return true;
        }

        // Allow critical lifecycle POST/PUT actions to finish active trips
        if (isRideRelated) {
            return path.endsWith("/complete") || 
                   path.endsWith("/cancel") || 
                   path.endsWith("/arrive") || 
                   path.endsWith("/start") ||
                   path.contains("/location");
        }

        return false;
    }

    @Override
    public int getOrder() {
        // Run after authentication
        return 0;
    }
}
