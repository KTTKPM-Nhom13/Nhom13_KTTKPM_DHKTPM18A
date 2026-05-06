package iuh.fit.common.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

public final class JwtSupport {
    private JwtSupport() {
    }

    public static JwtDecoder createJwtDecoder() throws Exception {
        return NimbusJwtDecoder.withPublicKey(loadPublicKey()).build();
    }

    public static RSAPublicKey loadPublicKey() throws Exception {
        String privateKeyPem = System.getenv("JWT_PRIVATE_KEY");
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            return derivePublicKey(privateKeyPem);
        }

        try {
            ClassPathResource privateKeyResource = new ClassPathResource("certs/private_key.pem");
            if (privateKeyResource.exists()) {
                String privatePem = new String(privateKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!privatePem.isBlank()) {
                    return derivePublicKey(privatePem);
                }
            }
        } catch (Exception ignored) {
            // Fall back to public_key.pem for services that only ship the public key.
        }

        ClassPathResource resource = new ClassPathResource("certs/public_key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPublicKey();
    }

    private static RSAPublicKey derivePublicKey(String privateKeyPem) throws Exception {
        RSAPrivateKey privateKey = JWK.parseFromPEMEncodedObjects(privateKeyPem).toRSAKey().toRSAPrivateKey();
        if (privateKey instanceof RSAPrivateCrtKey crtKey) {
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    crtKey.getModulus(),
                    crtKey.getPublicExponent()
            );
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        }
        throw new IllegalStateException("Unable to derive RSA public key from private key");
    }
}
