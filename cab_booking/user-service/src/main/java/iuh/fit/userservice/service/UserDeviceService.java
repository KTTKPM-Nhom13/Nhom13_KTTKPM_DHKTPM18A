package iuh.fit.userservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.userservice.dto.request.RegisterUserDeviceRequest;
import iuh.fit.userservice.dto.request.UpdateUserDeviceSessionRequest;
import iuh.fit.userservice.dto.response.UserDeviceResponse;
import iuh.fit.userservice.entity.DeviceSessionStatus;
import iuh.fit.userservice.entity.UserDevice;
import iuh.fit.userservice.entity.UserProfile;
import iuh.fit.userservice.repository.UserDeviceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserDeviceService {
    UserDeviceRepository userDeviceRepository;
    UserProfileService userProfileService;

    @Transactional(readOnly = true)
    public List<UserDeviceResponse> getMyDevices(String externalUserId) {
        UserProfile profile = userProfileService.getRequiredProfile(externalUserId);
        return userDeviceRepository.findByUserProfileOrderByLastSeenAtDesc(profile)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserDeviceResponse registerDevice(String externalUserId, RegisterUserDeviceRequest request) {
        UserProfile profile = userProfileService.getWritableProfileEntity(externalUserId);
        UserDevice device = userDeviceRepository.findByUserProfileAndDeviceIdentifier(profile, request.getDeviceIdentifier())
                .orElseGet(UserDevice::new);

        device.setUserProfile(profile);
        device.setDeviceIdentifier(request.getDeviceIdentifier());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setPlatform(request.getPlatform());
        device.setOsVersion(request.getOsVersion());
        device.setAppVersion(request.getAppVersion());
        device.setPushToken(request.getPushToken());
        device.setIpAddress(request.getIpAddress());
        device.setUserAgent(request.getUserAgent());
        device.setTrustedSession(request.isTrustedSession());
        device.setStatus(DeviceSessionStatus.ACTIVE);

        LocalDateTime now = LocalDateTime.now();
        if (device.getLastLoginAt() == null) {
            device.setLastLoginAt(now);
        } else {
            device.setLastLoginAt(now);
        }
        device.setLastSeenAt(now);

        return toResponse(userDeviceRepository.save(device));
    }

    @Transactional
    public UserDeviceResponse updateDevice(String externalUserId, UUID deviceId, UpdateUserDeviceSessionRequest request) {
        UserDevice device = getOwnedDevice(externalUserId, deviceId);

        if (request.getDeviceName() != null) {
            device.setDeviceName(request.getDeviceName());
        }
        if (request.getDeviceType() != null) {
            device.setDeviceType(request.getDeviceType());
        }
        if (request.getPlatform() != null) {
            device.setPlatform(request.getPlatform());
        }
        if (request.getOsVersion() != null) {
            device.setOsVersion(request.getOsVersion());
        }
        if (request.getAppVersion() != null) {
            device.setAppVersion(request.getAppVersion());
        }
        if (request.getPushToken() != null) {
            device.setPushToken(request.getPushToken());
        }
        if (request.getIpAddress() != null) {
            device.setIpAddress(request.getIpAddress());
        }
        if (request.getUserAgent() != null) {
            device.setUserAgent(request.getUserAgent());
        }
        if (request.getTrustedSession() != null) {
            device.setTrustedSession(request.getTrustedSession());
        }
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus());
        }

        device.setLastSeenAt(LocalDateTime.now());
        return toResponse(userDeviceRepository.save(device));
    }

    @Transactional
    public UserDeviceResponse touchDevice(String externalUserId, UUID deviceId) {
        UserDevice device = getOwnedDevice(externalUserId, deviceId);
        device.setLastSeenAt(LocalDateTime.now());
        if (device.getStatus() == DeviceSessionStatus.REVOKED) {
            device.setStatus(DeviceSessionStatus.ACTIVE);
        }
        return toResponse(userDeviceRepository.save(device));
    }

    @Transactional
    public UserDeviceResponse revokeDevice(String externalUserId, UUID deviceId) {
        UserDevice device = getOwnedDevice(externalUserId, deviceId);
        device.setStatus(DeviceSessionStatus.REVOKED);
        device.setLastSeenAt(LocalDateTime.now());
        return toResponse(userDeviceRepository.save(device));
    }

    private UserDevice getOwnedDevice(String externalUserId, UUID deviceId) {
        return userDeviceRepository.findByIdAndUserProfileExternalUserId(deviceId, externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_DEVICE_NOT_FOUND));
    }

    private UserDeviceResponse toResponse(UserDevice device) {
        return UserDeviceResponse.builder()
                .id(device.getId())
                .deviceIdentifier(device.getDeviceIdentifier())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .platform(device.getPlatform())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .pushToken(device.getPushToken())
                .ipAddress(device.getIpAddress())
                .userAgent(device.getUserAgent())
                .lastLoginAt(device.getLastLoginAt())
                .lastSeenAt(device.getLastSeenAt())
                .status(device.getStatus())
                .trustedSession(device.isTrustedSession())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
