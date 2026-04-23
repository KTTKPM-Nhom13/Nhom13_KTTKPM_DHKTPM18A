package iuh.fit.api_gateway.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/api/auth/**",
            "/eureka/**",
            "/eta/**",
            "/api/eta/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_URLS).permitAll()
                        .anyExchange().authenticated());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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
    public JwtDecoder jwtDecoder() throws Exception {
        ClassPathResource resource = new ClassPathResource("certs/public_key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
        RSAPublicKey publicKey = jwk.toRSAKey().toRSAPublicKey();

        // Trả về Decoder chuẩn của Spring
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
