package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterUserDeviceRequest {

    @NotBlank
    @Size(max = 120)
    String deviceIdentifier;

    @NotBlank
    @Size(max = 120)
    String deviceName;

    @NotNull
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

    boolean trustedSession;
}
