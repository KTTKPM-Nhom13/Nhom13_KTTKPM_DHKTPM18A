package iuh.fit.pricing_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PricingSecurityConfig {

    @Value("${app.security.public-endpoints:/actuator/**,/swagger-ui/**,/api-docs/**,/swagger-ui.html}")
    private String publicEndpoints;

    @Bean
    public SecurityFilterChain pricingSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(parsePublicEndpoints()).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pricing/estimate").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/pricing/confirm/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/pricing/calculate").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/pricing/surge/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/pricing/config").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/pricing/test-mapbox").hasAnyAuthority(
                                "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pricing/demand-supply").hasAnyAuthority(
                                "SCOPE_pricing:write", "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pricing/surge/**").hasAnyAuthority(
                                "SCOPE_pricing:write", "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/pricing/surge/**").hasAnyAuthority(
                                "SCOPE_pricing:write", "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    private String[] parsePublicEndpoints() {
        return publicEndpoints == null || publicEndpoints.isBlank()
                ? new String[]{"/actuator/**", "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html"}
                : publicEndpoints.split("\\s*,\\s*");
    }
}
