package iuh.fit.auth_service.service;

import iuh.fit.auth_service.entity.AuthUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthTokenService {
    final JwtEncoder jwtEncoder;

    @Value("${auth.jwt.access-token-minutes:120}")
    long accessTokenMinutes;

    public String generateAccessToken(AuthUser user, String deviceId, String platform) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenMinutes * 60))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl())
                .claim("phoneNumber", user.getPhoneNumber() == null ? "" : user.getPhoneNumber())
                .claim("role", user.getRole().name())
                .claim("scope", user.getRole().name())
                .claim("deviceId", deviceId)
                .claim("platform", platform)
                .claim("userId", user.getId().toString())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenMinutes * 60;
    }

    public String generateRefreshToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }
}
