package iuh.fit.common.security;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource; // Dùng Resource của Spring
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    private final String[] PUBLIC_ENDPOINTS = {
            "/**",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests( auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            // Đọc file từ folder resources/certs
            Resource resource = new ClassPathResource("certs/public_key.pem");
            String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
            RSAPublicKey publicKey = jwk.toRSAKey().toRSAPublicKey();

            return NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Not found Public Key: " + e.getMessage());
        }
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
