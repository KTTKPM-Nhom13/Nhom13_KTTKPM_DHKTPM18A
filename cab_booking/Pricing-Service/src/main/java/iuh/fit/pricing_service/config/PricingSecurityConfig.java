package iuh.fit.pricing_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class PricingSecurityConfig {

    @Value("${app.security.public-endpoints:/actuator/**,/swagger-ui/**,/api-docs/**,/swagger-ui.html}")
    private String publicEndpoints;

    @Bean
    @Order(1)
    public SecurityFilterChain pricingSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(parsePublicEndpoints()).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyAuthority(
                                "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/estimate").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/confirm/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/calculate").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pricing/surge/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pricing/config").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pricing/test-mapbox").hasAnyAuthority(
                                "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/demand-supply").hasAnyAuthority(
                                "SCOPE_pricing:write", "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/pricing/surge/**").hasAnyAuthority(
                                "SCOPE_pricing:write", "SCOPE_pricing:admin", "SCOPE_admin", "SCOPE_ADMIN", "ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new ScopeAndRoleAuthoritiesConverter(scopeAuthoritiesConverter));
        return jwtAuthenticationConverter;
    }

    private String[] parsePublicEndpoints() {
        return publicEndpoints == null || publicEndpoints.isBlank()
                ? new String[]{"/actuator/**", "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html"}
                : publicEndpoints.split("\\s*,\\s*");
    }

    private static final class ScopeAndRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter;

        private ScopeAndRoleAuthoritiesConverter(JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter) {
            this.scopeAuthoritiesConverter = scopeAuthoritiesConverter;
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>(scopeAuthoritiesConverter.convert(jwt));
            Object roleClaim = jwt.getClaim("role");
            if (roleClaim instanceof String role && !role.isBlank()) {
                authorities.add(toRoleAuthority(role));
            } else if (roleClaim instanceof Collection<?> roles) {
                roles.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(role -> !role.isBlank())
                        .map(ScopeAndRoleAuthoritiesConverter::toRoleAuthority)
                        .forEach(authorities::add);
            }
            return authorities;
        }

        private static GrantedAuthority toRoleAuthority(String role) {
            return new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role);
        }
    }
}
