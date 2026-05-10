package iuh.fit.userservice.dto.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SyncAccountLifecycleRequest {
    String accountStatus;
    LocalDateTime deletionRequestedAt;
    LocalDateTime scheduledDeletionAt;
    String deletionReason;
}
