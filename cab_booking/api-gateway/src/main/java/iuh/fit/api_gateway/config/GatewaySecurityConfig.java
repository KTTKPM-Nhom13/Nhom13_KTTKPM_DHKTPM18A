package iuh.fit.api_gateway.config;

import iuh.fit.common.config.JwtSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/api/auth/**",
            // "/api/users/**",
            // "/api/drivers/**",
            "/gateway/**",
            "/eureka/**",
            // "/eta/**",
            // "/api/eta/**",
            // "/api/notifications/**",
            // "/api/reviews/**",
            "/api/pricing/**",
            "/api/v1/ai-agent/health",
            "/api/v1/payments/momo/**",
            "/api/v1/payments/zalopay/**",
            "/api/v1/payments/vnpay/**",
            "/api/v1/payments/sepay/**",
            "/api/payments/momo/**",
            "/api/payments/zalopay/**",
            "/api/payments/vnpay/**",
            "/api/payments/sepay/**",
            // "/pricing/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder reactiveJwtDecoder) throws Exception {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_URLS).permitAll()
                        .pathMatchers("/api/notifications/**").permitAll()
                        .pathMatchers("/api/reviews/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder)));

        return http.build();
    }

    @Order(1)
    @Bean
    public SecurityWebFilterChain publicSecurityWebFilterChain(ServerHttpSecurity http) throws Exception {
        http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(PUBLIC_URLS))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(ex -> ex.anyExchange().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Served-By", "X-Debug-Trace-Id", "Retry-After", "X-RateLimit-Remaining",
                "X-RateLimit-Limit", "X-RateLimit-Burst-Capacity", "X-RateLimit-Replenish-Rate",
                "X-RateLimit-Requested-Tokens"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        return JwtSupport.createReactiveJwtDecoder();
    }

    @Bean
    public JwtDecoder blockingJwtDecoder() throws Exception {
        return JwtSupport.createJwtDecoder();
    }
}
