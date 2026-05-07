package iuh.fit.userservice.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountResponse {
    UUID profileId;
    String externalUserId;
    String fullName;
    String email;
    String phoneNumber;
    String avatarUrl;
    String accountStatus;
    LocalDateTime deletionRequestedAt;
    LocalDateTime scheduledDeletionAt;
    boolean restoreEligible;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
