package iuh.fit.auth_service.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;

public final class AuthJwtKeyLoader {
    private AuthJwtKeyLoader() {
    }

    public static RSAPrivateKey loadPrivateKey() throws Exception {
        String pem = null;

        var resource = new ClassPathResource("certs/private_key.pem");
        if (resource.exists()) {
            pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing private key file certs/private_key.pem in classpath");
        }
        return JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPrivateKey();
    }
}
