package iuh.fit.userservice.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileResponse {
    UUID id;
    String externalUserId;
    String fullName;
    String email;
    String phoneNumber;
    String avatarUrl;
    String gender;
    LocalDate dateOfBirth;
    String defaultPickupNote;
    String accountStatus;
    LocalDateTime deletionRequestedAt;
    LocalDateTime scheduledDeletionAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
