package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.entity.DeviceSessionStatus;
import iuh.fit.userservice.entity.DeviceType;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateUserDeviceSessionRequest {

    @Size(max = 120)
    String deviceName;

    DeviceType deviceType;

    @Size(max = 50)
    String platform;

    @Size(max = 50)
    String osVersion;

    @Size(max = 50)
    String appVersion;

    @Size(max = 500)
    String pushToken;

    @Size(max = 50)
    String ipAddress;

    @Size(max = 500)
    String userAgent;

    Boolean trustedSession;

    DeviceSessionStatus status;
}
