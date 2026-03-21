package iuh.fit.common.service;

import com.nimbusds.jose.jwk.JWK;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

@Component
@Slf4j
public class JwtUtils {

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            Resource resource = new ClassPathResource("certs/public_key.pem");
            String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
            this.publicKey = jwk.toRSAKey().toRSAPublicKey();

            log.info("JwtUtils: Loaded RSA Public Key successfully using Nimbus.");
        } catch (Exception e) {
            log.error("JwtUtils: Failed to load Public Key: {}", e.getMessage());
            throw new RuntimeException("Security initialization failed: " + e.getMessage());
        }
    }

    public Claims getClaims(String token) {
        // JJWT vẫn có thể sử dụng RSAPublicKey của Nimbus để verify
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }
}