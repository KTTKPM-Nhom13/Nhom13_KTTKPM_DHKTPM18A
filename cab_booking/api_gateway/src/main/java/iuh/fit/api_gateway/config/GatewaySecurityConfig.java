package iuh.fit.api_gateway.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/**").permitAll() // Cho phép login/register không cần token
                        .anyExchange().permitAll() // Đang dev nên để permitAll cho dễ test như bạn muốn
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        try {
            // Đọc file public_key.pem từ resources/certs (module common đã có)
            Resource resource = new ClassPathResource("certs/public_key.pem");
            String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
            RSAPublicKey publicKey = jwk.toRSAKey().toRSAPublicKey();

            return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            throw new RuntimeException("Gateway could not load Public Key: " + e.getMessage());
        }
    }
}