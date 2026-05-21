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
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                        String userId = jwt.getSubject();
                        
                        if (reactiveRedisTemplate != null) {
                            return reactiveRedisTemplate.opsForSet().isMember(BLOCKED_USERS_KEY, userId)
                                    .flatMap(isBlocked -> {
                                        if (Boolean.TRUE.equals(isBlocked)) {
                                            log.warn("Blocked user attempt: {}", userId);
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

    @Override
    public int getOrder() {
        // Run after authentication
        return 0;
    }
}
