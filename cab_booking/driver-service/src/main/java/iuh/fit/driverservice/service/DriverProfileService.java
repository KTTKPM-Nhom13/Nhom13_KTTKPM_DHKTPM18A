package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.dto.response.DriverEarningsSummaryResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.dto.response.DriverStatusCheckResponse;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverEarningRepository;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
    DriverStatusService driverStatusService;
    DriverEarningRepository driverEarningRepository;
    DriverLocationService driverLocationService;

    @Transactional
    public DriverProfileResponse getProfile(String externalUserId) {
        return toProfileResponse(getOrCreateProfileEntity(externalUserId));
    }

    @Transactional
    public DriverProfileResponse upsertProfile(String externalUserId, UpsertDriverProfileRequest request) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        profile.setFullName(request.getFullName());
        if (hasText(request.getEmail())) {
            profile.setEmail(request.getEmail().trim());
        }
        if (hasText(request.getPhoneNumber())) {
            profile.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (hasText(request.getAvatarUrl())) {
            profile.setAvatarUrl(request.getAvatarUrl().trim());
        }
        profile.setLicenseNumber(request.getLicenseNumber());
        profile.setVehicleType(request.getVehicleType());
        profile.setVehiclePlate(request.getVehiclePlate());
        profile.setVehicleModel(request.getVehicleModel());
        profile.setVehicleColor(request.getVehicleColor());
        if (request.getServiceArea() != null) {
            profile.setServiceArea(request.getServiceArea().trim());
        }

        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            profile.setVerificationStatus(DriverVerificationStatus.APPROVED);
            profile.setApprovedAt(LocalDateTime.now());
        }

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        driverStatusService.writeDriverStatus(savedProfile);
        return toProfileResponse(savedProfile);
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
        if (availabilityStatus == DriverAvailabilityStatus.ONLINE && profile.getCurrentRideId() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        profile.setAvailabilityStatus(availabilityStatus);
        profile.setCurrentLatitude(request.getCurrentLatitude());
        profile.setCurrentLongitude(request.getCurrentLongitude());
        if (availabilityStatus != DriverAvailabilityStatus.OFFLINE) {
            profile.setLastOnlineAt(LocalDateTime.now());
        }

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        driverStatusService.writeDriverStatus(savedProfile);

        // --- Availability GEO lifecycle (Phase 1) ---
        manageAvailabilityGeo(savedProfile, availabilityStatus);

        return toAvailabilityResponse(savedProfile);
    }

    /**
     * Heartbeat endpoint: ONLINE driver (without active ride) updates availability location.
     * Redis-only — no PostgreSQL writes. Coordinates are flushed to PG on state transitions
     * (ONLINE/OFFLINE/ride events). Heartbeat fires every 10-15s, so PG writes would be wasteful.
     */
    @Transactional(readOnly = true)
    public DriverAvailabilityResponse updateLocation(String externalUserId,
                                                      BigDecimal lat, BigDecimal lng) {
        DriverProfile profile = getRequiredProfile(externalUserId);

        if (profile.getAvailabilityStatus() != DriverAvailabilityStatus.ONLINE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (profile.getCurrentRideId() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        // Redis GEO only — no PG write on heartbeat
        driverLocationService.addToAvailableGeo(externalUserId,
                lat.doubleValue(), lng.doubleValue());

        return toAvailabilityResponse(profile);
    }

    /**
     * Manage the driver:available:locations Redis GEO key based on availability transitions.
     */
    private void manageAvailabilityGeo(DriverProfile profile, DriverAvailabilityStatus newStatus) {
        if (newStatus == DriverAvailabilityStatus.ONLINE
                && profile.getCurrentRideId() == null
                && profile.getCurrentLatitude() != null
                && profile.getCurrentLongitude() != null) {
            driverLocationService.addToAvailableGeo(
                    profile.getExternalUserId(),
                    profile.getCurrentLatitude().doubleValue(),
                    profile.getCurrentLongitude().doubleValue());
        } else if (newStatus == DriverAvailabilityStatus.OFFLINE
                || newStatus == DriverAvailabilityStatus.ON_TRIP) {
            driverLocationService.removeFromAvailableGeo(profile.getExternalUserId());
        }
    }

    @Transactional(readOnly = true)
    public DriverCurrentRideResponse getCurrentRide(String externalUserId) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        return toCurrentRideResponse(profile);
    }

    @Transactional(readOnly = true)
    public DriverEarningsSummaryResponse getEarningsSummary(String externalUserId) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        BigDecimal totalGrossAmount = driverEarningRepository.sumGrossAmountByDriverId(externalUserId);
        BigDecimal totalDriverAmount = driverEarningRepository.sumDriverAmountByDriverId(externalUserId);
        return DriverEarningsSummaryResponse.builder()
                .externalUserId(profile.getExternalUserId())
                .availabilityStatus(profile.getAvailabilityStatus().name())
                .totalCompletedRides(profile.getTotalCompletedRides())
                .averageRating(profile.getAverageRating())
                .totalEarnings(profile.getTotalEarnings())
                .totalGrossAmount(totalGrossAmount)
                .totalDriverAmount(totalDriverAmount)
                .currentRideActive(profile.getCurrentRideId() != null)
                .lastOnlineAt(profile.getLastOnlineAt())
                .build();
    }

    @Transactional
    public void updateDriverRating(String externalUserId, int newRating) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        
        BigDecimal currentAverage = profile.getAverageRating() != null ? profile.getAverageRating() : BigDecimal.ZERO;
        int currentTotalReviews = profile.getTotalReviews() != null ? profile.getTotalReviews() : 0;
        
        int newTotalReviews = currentTotalReviews + 1;
        
        // New Average = (CurrentAverage * TotalReviews + NewRating) / NewTotalReviews
        BigDecimal totalScore = currentAverage.multiply(new BigDecimal(currentTotalReviews))
                .add(new BigDecimal(newRating));
        
        BigDecimal newAverage = totalScore.divide(new BigDecimal(newTotalReviews), 2, RoundingMode.HALF_UP);
        
        profile.setAverageRating(newAverage);
        profile.setTotalReviews(newTotalReviews);
        
        driverProfileRepository.save(profile);
    }

    @Transactional
    public DriverStatusCheckResponse checkAvailability(String externalUserId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        driverStatusService.writeDriverStatus(profile);
        return toStatusCheckResponse(profile);
    }

    @Transactional
    public DriverProfile getOrCreateProfileEntity(String externalUserId) {
        DriverProfile profile = driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    DriverProfile created = new DriverProfile();
                    created.setExternalUserId(externalUserId);
                    created.setFullName(resolveCurrentUserClaim("fullName", "Driver " + externalUserId));
                    created.setEmail(resolveCurrentUserClaim("email", null));
                    created.setPhoneNumber(resolveCurrentUserClaim("phoneNumber", null));
                    created.setAvatarUrl(resolveCurrentUserClaim("avatarUrl", null));
                    created.setAverageRating(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    created.setTotalEarnings(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return driverProfileRepository.save(created);
                });
        syncMissingAuthSnapshot(profile);
        return profile;
    }

    private DriverProfile getRequiredProfile(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
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
                .vehicleType(profile.getVehicleType() == null ? null : profile.getVehicleType().name())
                .vehiclePlate(profile.getVehiclePlate())
                .vehicleModel(profile.getVehicleModel())
                .vehicleColor(profile.getVehicleColor())
                .serviceArea(profile.getServiceArea())
                .availabilityStatus(profile.getAvailabilityStatus().name())
                .verificationStatus(profile.getVerificationStatus().name())
                .accountStatus(profile.getAccountStatus().name())
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

    private DriverStatusCheckResponse toStatusCheckResponse(DriverProfile profile) {
        DriverAvailabilityStatus availabilityStatus = profile.getAvailabilityStatus();
        return DriverStatusCheckResponse.builder()
                .externalUserId(profile.getExternalUserId())
                .availabilityStatus(availabilityStatus.name())
                .online(availabilityStatus == DriverAvailabilityStatus.ONLINE
                        || availabilityStatus == DriverAvailabilityStatus.ON_TRIP)
                .offline(availabilityStatus == DriverAvailabilityStatus.OFFLINE)
                .activeForBooking(driverStatusService.isActiveForBooking(profile))
                .verificationStatus(profile.getVerificationStatus().name())
                .currentRideId(profile.getCurrentRideId())
                .currentRideStatus(driverStatusService.currentRideStatusName(profile))
                .currentLatitude(profile.getCurrentLatitude())
                .currentLongitude(profile.getCurrentLongitude())
                .lastOnlineAt(profile.getLastOnlineAt())
                .build();
    }

    private DriverCurrentRideResponse toCurrentRideResponse(DriverProfile profile) {
        return DriverCurrentRideResponse.builder()
                .rideId(profile.getCurrentRideId())
                .bookingId(profile.getCurrentBookingId())
                .customerId(profile.getCurrentRideCustomerId())
                .rideStatus(profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name())
                .pickupAddress(profile.getCurrentRidePickup())
                .destinationAddress(profile.getCurrentRideDestination())
                .pickupLocation(toLocationPayload(profile.getCurrentRidePickupLat(), profile.getCurrentRidePickupLng()))
                .destinationLocation(toLocationPayload(profile.getCurrentRideDropoffLat(), profile.getCurrentRideDropoffLng()))
                .vehicleType(profile.getCurrentRideVehicleType())
                .paymentMethod(profile.getCurrentRidePaymentMethod())
                .estimatedFare(profile.getCurrentRideEstimatedFare())
                .requestedAt(profile.getCurrentRideRequestedAt())
                .driverAvailabilityStatus(profile.getAvailabilityStatus().name())
                .currentLocation(toLocationPayload(profile))
                .build();
    }

    private DriverLocationPayload toLocationPayload(DriverProfile profile) {
        return toLocationPayload(profile.getCurrentLatitude(), profile.getCurrentLongitude());
    }

    private DriverLocationPayload toLocationPayload(BigDecimal lat, BigDecimal lng) {
        if (lat == null && lng == null) {
            return null;
        }
        return DriverLocationPayload.builder()
                .lat(lat)
                .lng(lng)
                .build();
    }

    private void syncMissingAuthSnapshot(DriverProfile profile) {
        boolean dirty = false;

        if (!hasText(profile.getFullName())) {
            profile.setFullName(resolveCurrentUserClaim("fullName", "Driver " + profile.getExternalUserId()));
            dirty = true;
        }
        if (!hasText(profile.getEmail())) {
            String email = resolveCurrentUserClaim("email", null);
            if (hasText(email)) {
                profile.setEmail(email);
                dirty = true;
            }
        }
        if (!hasText(profile.getPhoneNumber())) {
            String phoneNumber = normalizeNullableClaim(resolveCurrentUserClaim("phoneNumber", null));
            if (hasText(phoneNumber)) {
                profile.setPhoneNumber(phoneNumber);
                dirty = true;
            }
        }
        if (!hasText(profile.getAvatarUrl())) {
            String avatarUrl = normalizeNullableClaim(resolveCurrentUserClaim("avatarUrl", null));
            if (hasText(avatarUrl)) {
                profile.setAvatarUrl(avatarUrl);
                dirty = true;
            }
        }

        if (dirty) {
            driverProfileRepository.save(profile);
        }
    }

    private String resolveCurrentUserClaim(String claimName, String fallbackValue) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return normalizeNullableClaim(jwt.getClaimAsString(claimName), fallbackValue);
        }
        return fallbackValue;
    }

    private String normalizeNullableClaim(String value) {
        return normalizeNullableClaim(value, null);
    }

    private String normalizeNullableClaim(String value, String fallbackValue) {
        if (!hasText(value)) {
            return fallbackValue;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
