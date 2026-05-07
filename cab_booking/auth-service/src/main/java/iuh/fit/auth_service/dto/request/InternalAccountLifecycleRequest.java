package iuh.fit.auth_service.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalAccountLifecycleRequest {
    String accountStatus;
    LocalDateTime deletionRequestedAt;
    LocalDateTime scheduledDeletionAt;
    String deletionReason;
}
