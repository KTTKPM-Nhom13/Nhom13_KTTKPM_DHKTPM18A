package iuh.fit.auth_service.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthTokenResponse {
    String accessToken;
    String refreshToken;
    String tokenType;
    long expiresInSeconds;
    String deviceId;
    String platform;
    AuthUserSummaryResponse user;
}
