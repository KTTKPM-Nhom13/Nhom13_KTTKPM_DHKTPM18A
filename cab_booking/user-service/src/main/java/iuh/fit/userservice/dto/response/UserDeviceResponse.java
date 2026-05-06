package iuh.fit.userservice.dto.response;

import iuh.fit.userservice.entity.DeviceSessionStatus;
import iuh.fit.userservice.entity.DeviceType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserDeviceResponse {
    UUID id;
    String deviceIdentifier;
    String deviceName;
    DeviceType deviceType;
    String platform;
    String osVersion;
    String appVersion;
    String pushToken;
    String ipAddress;
    String userAgent;
    LocalDateTime lastLoginAt;
    LocalDateTime lastSeenAt;
    DeviceSessionStatus status;
    boolean trustedSession;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
