package iuh.fit.auth_service;

import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;

public class ExtractKeyTest {

    @Test
    public void testExtractPublicKey() throws Exception {
        var resource = new ClassPathResource("certs/private_key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        RSAPrivateKey privateKey = JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPrivateKey();
        if (privateKey instanceof RSAPrivateCrtKey) {
            RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    crtKey.getModulus(),
                    crtKey.getPublicExponent()
            );
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
            
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
            System.out.println("-----BEGIN PUBLIC KEY-----");
            System.out.println(encoded);
            System.out.println("-----END PUBLIC KEY-----");
        } else {
            System.out.println("Not a CRT Private Key");
        }
    }
}
