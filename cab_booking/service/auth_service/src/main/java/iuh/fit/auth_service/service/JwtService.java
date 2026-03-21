package iuh.fit.auth_service.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtService {
    @Value("${JWT_PRIVATE_KEY}")
    private String privateKey;

    public String generateToken(String userName) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet
                .Builder()
                .subject(userName)
                .issuer("http://cab-booking.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()
                ))
                .build();

        SignedJWT signedJWT = new SignedJWT(header, jwtClaimsSet);

        try {
            JWK jwk = JWK.parseFromPEMEncodedObjects(privateKey);
            RSAKey rsaKey = jwk.toRSAKey();

            JWSSigner signer = new RSASSASigner(rsaKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Could not sign JWT", e);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JWT", e);
        }
    }
}
