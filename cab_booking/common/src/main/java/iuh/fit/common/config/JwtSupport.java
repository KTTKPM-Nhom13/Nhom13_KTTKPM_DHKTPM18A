package iuh.fit.common.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

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

    public static ReactiveJwtDecoder createReactiveJwtDecoder() throws Exception {
        return NimbusReactiveJwtDecoder.withPublicKey(loadPublicKey()).build();
    }

    public static RSAPublicKey loadPublicKey() throws Exception {
        try {
            ClassPathResource publicKeyResource = new ClassPathResource("certs/public_key.pem");
            if (publicKeyResource.exists()) {
                String publicPem = new String(publicKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!publicPem.isBlank()) {
                    return JWK.parseFromPEMEncodedObjects(publicPem).toRSAKey().toRSAPublicKey();
                }
            }
        } catch (Exception ignored) {
            // Fall back to private_key.pem or env if public key is not present.
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
            // Fall back to env key if classpath keys are missing.
        }

        // Require classpath resource files under `certs/` (do not fall back to env).
        throw new IllegalStateException("No JWT public/private key available on classpath (certs/public_key.pem or certs/private_key.pem)");
    }

    private static RSAPublicKey derivePublicKey(String privateKeyPem) throws Exception {
        RSAPrivateKey privateKey = JWK.parseFromPEMEncodedObjects(privateKeyPem).toRSAKey().toRSAPrivateKey();
        if (privateKey instanceof RSAPrivateCrtKey) {
            RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    crtKey.getModulus(),
                    crtKey.getPublicExponent()
            );
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        }
        throw new IllegalStateException("Unable to derive RSA public key from private key");
    }
}
