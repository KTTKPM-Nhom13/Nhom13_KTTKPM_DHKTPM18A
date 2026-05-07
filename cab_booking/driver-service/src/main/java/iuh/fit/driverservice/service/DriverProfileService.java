package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.request.CompleteDriverRideRequest;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverRideProgressRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.dto.response.DriverEarningsSummaryResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.entity.DriverAssignmentAction;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverProfileService {
    DriverProfileRepository driverProfileRepository;

    @Transactional
    public DriverProfileResponse getProfile(String externalUserId) {
        return toProfileResponse(getOrCreateProfileEntity(externalUserId));
    }

    @Transactional
    public DriverProfileResponse upsertProfile(String externalUserId, UpsertDriverProfileRequest request) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        profile.setFullName(request.getFullName());
        profile.setEmail(request.getEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setLicenseNumber(request.getLicenseNumber());
        profile.setVehicleType(request.getVehicleType());
        profile.setVehiclePlate(request.getVehiclePlate());
        profile.setVehicleModel(request.getVehicleModel());
        profile.setVehicleColor(request.getVehicleColor());
        profile.setServiceArea(request.getServiceArea());

        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            profile.setVerificationStatus(DriverVerificationStatus.APPROVED);
            profile.setApprovedAt(LocalDateTime.now());
        }

        return toProfileResponse(driverProfileRepository.save(profile));
    }

    @Transactional
    public DriverAvailabilityResponse updateAvailability(String externalUserId, UpdateDriverAvailabilityRequest request) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        DriverAvailabilityStatus availabilityStatus = parseAvailabilityStatus(request.getAvailabilityStatus());

        if (availabilityStatus == DriverAvailabilityStatus.ONLINE
                && profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (availabilityStatus == DriverAvailabilityStatus.ON_TRIP && profile.getCurrentRideId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        profile.setAvailabilityStatus(availabilityStatus);
        profile.setCurrentLatitude(request.getCurrentLatitude());
        profile.setCurrentLongitude(request.getCurrentLongitude());
        if (availabilityStatus != DriverAvailabilityStatus.OFFLINE) {
            profile.setLastOnlineAt(LocalDateTime.now());
        }

        return toAvailabilityResponse(driverProfileRepository.save(profile));
    }

    @Transactional
    public DriverCurrentRideResponse handleAssignment(String externalUserId, HandleDriverAssignmentRequest request) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        DriverAssignmentAction action = DriverAssignmentAction.valueOf(request.getAction().trim().toUpperCase());

        if (action == DriverAssignmentAction.REJECT) {
            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            return toCurrentRideResponse(driverProfileRepository.save(profile));
        }

        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.OFFLINE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        profile.setCurrentRideId(request.getRideId());
        profile.setCurrentRideStatus(DriverRideStatus.ACCEPTED);
        profile.setCurrentRidePickup(request.getPickupAddress());
        profile.setCurrentRideDestination(request.getDestinationAddress());
        profile.setCurrentRideRequestedAt(
                request.getRequestedAt() == null ? LocalDateTime.now() : request.getRequestedAt());
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        return toCurrentRideResponse(driverProfileRepository.save(profile));
    }

    @Transactional
    public DriverCurrentRideResponse updateRideProgress(String externalUserId, UpdateDriverRideProgressRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);

        profile.setCurrentRideStatus(DriverRideStatus.valueOf(request.getRideStatus().trim().toUpperCase()));
        if (request.getCurrentLatitude() != null) {
            profile.setCurrentLatitude(request.getCurrentLatitude());
        }
        if (request.getCurrentLongitude() != null) {
            profile.setCurrentLongitude(request.getCurrentLongitude());
        }
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        return toCurrentRideResponse(driverProfileRepository.save(profile));
    }

    @Transactional
    public DriverCurrentRideResponse completeCurrentRide(String externalUserId, CompleteDriverRideRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);

        profile.setTotalCompletedRides(profile.getTotalCompletedRides() + 1);
        profile.setTotalEarnings(profile.getTotalEarnings().add(request.getFareAmount()));
        profile.setCurrentRideStatus(DriverRideStatus.COMPLETED);
        profile.setLastOnlineAt(request.getCompletedAt() == null ? LocalDateTime.now() : request.getCompletedAt());

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        return toCurrentRideResponse(driverProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public DriverCurrentRideResponse getCurrentRide(String externalUserId) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        return toCurrentRideResponse(profile);
    }

    @Transactional(readOnly = true)
    public DriverEarningsSummaryResponse getEarningsSummary(String externalUserId) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        return DriverEarningsSummaryResponse.builder()
                .externalUserId(profile.getExternalUserId())
                .availabilityStatus(profile.getAvailabilityStatus().name())
                .totalCompletedRides(profile.getTotalCompletedRides())
                .averageRating(profile.getAverageRating())
                .totalEarnings(profile.getTotalEarnings())
                .currentRideActive(profile.getCurrentRideId() != null)
                .lastOnlineAt(profile.getLastOnlineAt())
                .build();
    }

    @Transactional
    public DriverProfile getOrCreateProfileEntity(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    DriverProfile profile = new DriverProfile();
                    profile.setExternalUserId(externalUserId);
                    profile.setFullName("Driver " + externalUserId);
                    profile.setAverageRating(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    profile.setTotalEarnings(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return driverProfileRepository.save(profile);
                });
    }

    private DriverProfile getRequiredProfile(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private void ensureCurrentRideExists(DriverProfile profile) {
        if (profile.getCurrentRideId() == null || profile.getCurrentRideId().isBlank()) {
            throw new AppException(ErrorCode.USER_PROFILE_NOT_FOUND);
        }
    }

    private void clearCurrentRide(DriverProfile profile, DriverAvailabilityStatus nextAvailabilityStatus) {
        profile.setCurrentRideId(null);
        profile.setCurrentRideStatus(null);
        profile.setCurrentRidePickup(null);
        profile.setCurrentRideDestination(null);
        profile.setCurrentRideRequestedAt(null);
        profile.setAvailabilityStatus(nextAvailabilityStatus);
    }

    private DriverAvailabilityStatus parseAvailabilityStatus(String rawStatus) {
        return DriverAvailabilityStatus.valueOf(rawStatus.trim().toUpperCase());
    }

    private DriverProfileResponse toProfileResponse(DriverProfile profile) {
        return DriverProfileResponse.builder()
                .id(profile.getId())
                .externalUserId(profile.getExternalUserId())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .phoneNumber(profile.getPhoneNumber())
                .avatarUrl(profile.getAvatarUrl())
                .licenseNumber(profile.getLicenseNumber())
                .vehicleType(profile.getVehicleType())
                .vehiclePlate(profile.getVehiclePlate())
                .vehicleModel(profile.getVehicleModel())
                .vehicleColor(profile.getVehicleColor())
                .serviceArea(profile.getServiceArea())
                .availabilityStatus(profile.getAvailabilityStatus().name())
                .verificationStatus(profile.getVerificationStatus().name())
                .currentLatitude(profile.getCurrentLatitude())
                .currentLongitude(profile.getCurrentLongitude())
                .lastOnlineAt(profile.getLastOnlineAt())
                .approvedAt(profile.getApprovedAt())
                .totalCompletedRides(profile.getTotalCompletedRides())
                .averageRating(profile.getAverageRating())
                .totalEarnings(profile.getTotalEarnings())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private DriverAvailabilityResponse toAvailabilityResponse(DriverProfile profile) {
        return DriverAvailabilityResponse.builder()
                .externalUserId(profile.getExternalUserId())
                .availabilityStatus(profile.getAvailabilityStatus().name())
                .verificationStatus(profile.getVerificationStatus().name())
                .currentLatitude(profile.getCurrentLatitude())
                .currentLongitude(profile.getCurrentLongitude())
                .lastOnlineAt(profile.getLastOnlineAt())
                .build();
    }

    private DriverCurrentRideResponse toCurrentRideResponse(DriverProfile profile) {
        return DriverCurrentRideResponse.builder()
                .rideId(profile.getCurrentRideId())
                .rideStatus(profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name())
                .pickupAddress(profile.getCurrentRidePickup())
                .destinationAddress(profile.getCurrentRideDestination())
                .requestedAt(profile.getCurrentRideRequestedAt())
                .driverAvailabilityStatus(profile.getAvailabilityStatus().name())
                .build();
    }
}
