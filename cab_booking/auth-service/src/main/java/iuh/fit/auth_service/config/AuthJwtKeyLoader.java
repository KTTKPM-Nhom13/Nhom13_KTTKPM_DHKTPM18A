package iuh.fit.auth_service.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;

public final class AuthJwtKeyLoader {
    private AuthJwtKeyLoader() {
    }

    public static RSAPrivateKey loadPrivateKey() throws Exception {
        String pem = System.getenv("JWT_PRIVATE_KEY");
        if (pem == null || pem.isBlank()) {
            var resource = new ClassPathResource("certs/private_key.pem");
            pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPrivateKey();
    }
}
