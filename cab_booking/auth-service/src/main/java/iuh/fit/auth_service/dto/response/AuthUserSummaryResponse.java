package iuh.fit.auth_service.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthUserSummaryResponse {
    UUID userId;
    String email;
    String fullName;
    String avatarUrl;
    String phoneNumber;
    String role;
    boolean emailVerified;
    String accountStatus;
    LocalDateTime scheduledDeletionAt;
    LocalDateTime lastLoginAt;
}
