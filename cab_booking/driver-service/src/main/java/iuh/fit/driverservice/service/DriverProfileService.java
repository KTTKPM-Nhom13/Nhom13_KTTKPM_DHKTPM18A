package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.event.DriverStatusEvent;
import iuh.fit.driverservice.dto.event.RideAcceptRequestedEvent;
import iuh.fit.driverservice.dto.event.RideAcceptedEvent;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.event.RideCancelledEvent;
import iuh.fit.driverservice.dto.event.RideCompletedEvent;
import iuh.fit.driverservice.dto.event.RideFinishedEvent;
import iuh.fit.driverservice.dto.event.RideProgressEvent;
import iuh.fit.driverservice.dto.event.RideRejectedEvent;
import iuh.fit.driverservice.dto.event.RideRejectRequestedEvent;
import iuh.fit.driverservice.dto.request.CompleteDriverRideRequest;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverRideProgressRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.dto.response.DriverEarningsSummaryResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.dto.response.DriverStatusCheckResponse;
import iuh.fit.driverservice.entity.DriverAssignmentAction;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverProfileService {
    static final String RIDE_ACCEPT_REQUESTED_TOPIC = "ride.accept.requested";
    static final String RIDE_REJECT_REQUESTED_TOPIC = "ride.reject.requested";
    static final String RIDE_ACCEPTED_TOPIC = "ride.accepted";
    static final String RIDE_REJECTED_TOPIC = "ride.rejected";
    static final String RIDE_ARRIVED_TOPIC = "ride.arrived";
    static final String RIDE_STARTED_TOPIC = "ride.started";
    static final String RIDE_COMPLETED_TOPIC = "ride.completed";
    static final String RIDE_FINISHED_LEGACY_TOPIC = "ride.finished";
    static final String DRIVER_STATUS_CHANGED_TOPIC = "driver.status.changed";
    static final String DRIVER_STATUS_PREFIX = "driver:status:";
    static final Duration ASSIGNED_STATUS_TTL = Duration.ofMinutes(5);
    static final Duration BUSY_STATUS_TTL = Duration.ofHours(12);

    DriverProfileRepository driverProfileRepository;
    KafkaTemplate<String, Object> kafkaTemplate;
    StringRedisTemplate stringRedisTemplate;

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
        writeDriverStatus(savedProfile);
        publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), currentRideStatusName(savedProfile));
        return toAvailabilityResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse handleAssignment(String externalUserId, HandleDriverAssignmentRequest request) {
        DriverProfile profile = getOrCreateProfileEntity(externalUserId);
        DriverAssignmentAction action = DriverAssignmentAction.valueOf(request.getAction().trim().toUpperCase());
        String requestedRideId = request.getRideId();

        if (action == DriverAssignmentAction.REJECT) {
            ensureRideMatchesCurrentAssignment(profile, requestedRideId);
            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            DriverProfile savedProfile = driverProfileRepository.save(profile);
            writeDriverStatus(savedProfile);
            publishRideRejected(requestedRideId, savedProfile.getExternalUserId(), "Driver rejected assignment");
            publishDriverStatusChanged(savedProfile, requestedRideId, "REJECTED");
            return toCurrentRideResponse(savedProfile);
        }

        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.OFFLINE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        ensureRideMatchesCurrentAssignment(profile, requestedRideId);

        profile.setCurrentRideId(request.getRideId());
        profile.setCurrentRideStatus(DriverRideStatus.ACCEPTED);
        profile.setCurrentRidePickup(request.getPickupAddress());
        profile.setCurrentRideDestination(request.getDestinationAddress());
        profile.setCurrentRideRequestedAt(
                request.getRequestedAt() == null ? LocalDateTime.now() : request.getRequestedAt());
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishRideAccepted(savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), currentRideStatusName(savedProfile));
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse acceptRide(String externalUserId, String rideId) {
        HandleDriverAssignmentRequest request = new HandleDriverAssignmentRequest();
        request.setRideId(rideId);
        request.setAction(DriverAssignmentAction.ACCEPT.name());
        return handleAssignment(externalUserId, request);
    }

    @Transactional
    public DriverCurrentRideResponse rejectRide(String externalUserId, String rideId) {
        HandleDriverAssignmentRequest request = new HandleDriverAssignmentRequest();
        request.setRideId(rideId);
        request.setAction(DriverAssignmentAction.REJECT.name());
        return handleAssignment(externalUserId, request);
    }

    @Transactional
    public void handleRideAssigned(RideAssignedEvent event) {
        DriverProfile profile = getOrCreateProfileEntity(event.getDriverId());
        if (profile.getCurrentRideId() != null && !profile.getCurrentRideId().equals(event.getRideId())) {
            return;
        }
        if (profile.getCurrentRideId() != null && profile.getCurrentRideStatus() != null) {
            return;
        }

        profile.setCurrentRideId(event.getRideId());
        profile.setCurrentRideStatus(DriverRideStatus.ASSIGNED);
        profile.setLastOnlineAt(LocalDateTime.now());
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), currentRideStatusName(savedProfile));
    }

    @Transactional
    public void handleRideCancelled(RideCancelledEvent event) {
        if (event.getDriverId() == null || event.getDriverId().isBlank()) {
            return;
        }

        DriverProfile profile = driverProfileRepository.findByExternalUserId(event.getDriverId()).orElse(null);
        if (profile == null || profile.getCurrentRideId() == null || !profile.getCurrentRideId().equals(event.getRideId())) {
            return;
        }

        String cancelledRideId = profile.getCurrentRideId();
        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishDriverStatusChanged(savedProfile, cancelledRideId, "CANCELLED");
    }

    @Transactional
    public void handleRideAccepted(RideAcceptedEvent event) {
        DriverProfile profile = driverProfileRepository.findByExternalUserId(event.getDriverId()).orElse(null);
        if (profile == null || profile.getCurrentRideId() == null || !profile.getCurrentRideId().equals(event.getRideId())) {
            return;
        }

        profile.setCurrentRideStatus(DriverRideStatus.ACCEPTED);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), currentRideStatusName(savedProfile));
    }

    @Transactional
    public DriverCurrentRideResponse updateRideProgress(String externalUserId, UpdateDriverRideProgressRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);

        DriverRideStatus nextStatus = DriverRideStatus.valueOf(request.getRideStatus().trim().toUpperCase());
        validateRideStatusTransition(profile.getCurrentRideStatus(), nextStatus);
        profile.setCurrentRideStatus(nextStatus);
        if (request.getCurrentLatitude() != null) {
            profile.setCurrentLatitude(request.getCurrentLatitude());
        }
        if (request.getCurrentLongitude() != null) {
            profile.setCurrentLongitude(request.getCurrentLongitude());
        }
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        if (nextStatus == DriverRideStatus.ARRIVED_PICKUP) {
            publishRideProgress(RIDE_ARRIVED_TOPIC, "RideArrived", savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        } else if (nextStatus == DriverRideStatus.IN_PROGRESS) {
            publishRideProgress(RIDE_STARTED_TOPIC, "RideStarted", savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        }
        publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), currentRideStatusName(savedProfile));
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse arriveAtPickup(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);
        ensureRideMatchesCurrentAssignment(profile, rideId);
        DriverRideStatus currentStatus = profile.getCurrentRideStatus();
        if (currentStatus != DriverRideStatus.ACCEPTED && currentStatus != DriverRideStatus.EN_ROUTE_PICKUP) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        profile.setCurrentRideStatus(DriverRideStatus.ARRIVED_PICKUP);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishRideProgress(RIDE_ARRIVED_TOPIC, "RideArrived", rideId, savedProfile.getExternalUserId());
        publishDriverStatusChanged(savedProfile, rideId, currentRideStatusName(savedProfile));
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse startRide(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);
        ensureRideMatchesCurrentAssignment(profile, rideId);
        DriverRideStatus currentStatus = profile.getCurrentRideStatus();
        if (currentStatus != DriverRideStatus.ARRIVED_PICKUP && currentStatus != DriverRideStatus.EN_ROUTE_PICKUP) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        profile.setCurrentRideStatus(DriverRideStatus.IN_PROGRESS);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishRideProgress(RIDE_STARTED_TOPIC, "RideStarted", rideId, savedProfile.getExternalUserId());
        publishDriverStatusChanged(savedProfile, rideId, currentRideStatusName(savedProfile));
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse completeCurrentRide(String externalUserId, CompleteDriverRideRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);
        String completedRideId = profile.getCurrentRideId();

        profile.setTotalCompletedRides(profile.getTotalCompletedRides() + 1);
        profile.setTotalEarnings(profile.getTotalEarnings().add(request.getFareAmount()));
        profile.setCurrentRideStatus(DriverRideStatus.COMPLETED);
        profile.setLastOnlineAt(request.getCompletedAt() == null ? LocalDateTime.now() : request.getCompletedAt());

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writeDriverStatus(savedProfile);
        publishRideCompleted(completedRideId, savedProfile.getExternalUserId(), request);
        publishDriverStatusChanged(savedProfile, completedRideId, DriverRideStatus.COMPLETED.name());
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse completeRide(String externalUserId, String rideId, CompleteDriverRideRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureCurrentRideExists(profile);
        ensureRideMatchesCurrentAssignment(profile, rideId);
        return completeCurrentRide(externalUserId, request);
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
        writeDriverStatus(profile);
        publishDriverStatusChanged(profile, profile.getCurrentRideId(), currentRideStatusName(profile));
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

    private void ensureCurrentRideExists(DriverProfile profile) {
        if (profile.getCurrentRideId() == null || profile.getCurrentRideId().isBlank()) {
            throw new AppException(ErrorCode.USER_PROFILE_NOT_FOUND);
        }
    }

    private void ensureRideMatchesCurrentAssignment(DriverProfile profile, String rideId) {
        if (profile.getCurrentRideId() != null && !profile.getCurrentRideId().equals(rideId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
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

    private void validateRideStatusTransition(DriverRideStatus currentStatus, DriverRideStatus nextStatus) {
        if (currentStatus == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        if (currentStatus == DriverRideStatus.ACCEPTED && nextStatus == DriverRideStatus.EN_ROUTE_PICKUP) {
            return;
        }
        if (currentStatus == DriverRideStatus.EN_ROUTE_PICKUP
                && (nextStatus == DriverRideStatus.ARRIVED_PICKUP || nextStatus == DriverRideStatus.IN_PROGRESS)) {
            return;
        }
        if (currentStatus == DriverRideStatus.ARRIVED_PICKUP && nextStatus == DriverRideStatus.IN_PROGRESS) {
            return;
        }
        throw new AppException(ErrorCode.VALIDATION_ERROR);
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

    private DriverStatusCheckResponse toStatusCheckResponse(DriverProfile profile) {
        DriverAvailabilityStatus availabilityStatus = profile.getAvailabilityStatus();
        return DriverStatusCheckResponse.builder()
                .externalUserId(profile.getExternalUserId())
                .availabilityStatus(availabilityStatus.name())
                .online(availabilityStatus == DriverAvailabilityStatus.ONLINE
                        || availabilityStatus == DriverAvailabilityStatus.ON_TRIP)
                .offline(availabilityStatus == DriverAvailabilityStatus.OFFLINE)
                .activeForBooking(isActiveForBooking(profile))
                .verificationStatus(profile.getVerificationStatus().name())
                .currentRideId(profile.getCurrentRideId())
                .currentRideStatus(currentRideStatusName(profile))
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
                .currentLocation(toLocationPayload(profile))
                .build();
    }

    private void publishRideAcceptRequested(String rideId, String driverId) {
        kafkaTemplate.send(
                RIDE_ACCEPT_REQUESTED_TOPIC,
                rideId,
                RideAcceptRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(RideAcceptRequestedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .driverId(driverId)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishRideRejectRequested(String rideId, String driverId, String reason) {
        kafkaTemplate.send(
                RIDE_REJECT_REQUESTED_TOPIC,
                rideId,
                RideRejectRequestedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(RideRejectRequestedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .driverId(driverId)
                        .reason(reason)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishRideAccepted(String rideId, String driverId) {
        kafkaTemplate.send(
                RIDE_ACCEPTED_TOPIC,
                rideId,
                RideAcceptedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type("RideAccepted")
                        .rideId(rideId)
                        .driverId(driverId)
                        .status(DriverRideStatus.ACCEPTED.name())
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishRideRejected(String rideId, String driverId, String reason) {
        kafkaTemplate.send(
                RIDE_REJECTED_TOPIC,
                rideId,
                RideRejectedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(RideRejectedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .driverId(driverId)
                        .reason(reason)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishRideProgress(String topic, String type, String rideId, String driverId) {
        kafkaTemplate.send(
                topic,
                rideId,
                RideProgressEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(type)
                        .rideId(rideId)
                        .driverId(driverId)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishRideCompleted(String rideId, String driverId, CompleteDriverRideRequest request) {
        RideCompletedEvent event = RideCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideCompletedEvent.EVENT_TYPE)
                .rideId(rideId)
                .driverId(driverId)
                .finalFare(request.getFareAmount())
                .paymentMethod("CASH")
                .timestamp(Instant.now().toString())
                .build();
        kafkaTemplate.send(RIDE_COMPLETED_TOPIC, rideId, event);
        kafkaTemplate.send(
                RIDE_FINISHED_LEGACY_TOPIC,
                rideId,
                RideFinishedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(RideFinishedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .driverId(driverId)
                        .finalFare(request.getFareAmount())
                        .paymentMethod("CASH")
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishDriverStatusChanged(DriverProfile profile, String rideId, String rideStatus) {
        kafkaTemplate.send(
                DRIVER_STATUS_CHANGED_TOPIC,
                DriverStatusEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(DriverStatusEvent.EVENT_TYPE)
                        .driverId(profile.getExternalUserId())
                        .availabilityStatus(profile.getAvailabilityStatus().name())
                        .activeForBooking(isActiveForBooking(profile))
                        .rideId(rideId)
                        .rideStatus(rideStatus)
                        .currentLocation(toLocationPayload(profile))
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void writeDriverStatus(DriverProfile profile) {
        String status = redisStatusFor(profile);
        String key = DRIVER_STATUS_PREFIX + profile.getExternalUserId();
        if ("ASSIGNED".equals(status)) {
            stringRedisTemplate.opsForValue().set(key, status, ASSIGNED_STATUS_TTL);
        } else if ("BUSY".equals(status)) {
            stringRedisTemplate.opsForValue().set(key, status, BUSY_STATUS_TTL);
        } else {
            stringRedisTemplate.opsForValue().set(key, status);
        }
    }

    private String redisStatusFor(DriverProfile profile) {
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.OFFLINE) {
            return "OFFLINE";
        }
        DriverRideStatus rideStatus = profile.getCurrentRideStatus();
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE && rideStatus == null) {
            return "AVAILABLE";
        }
        if (rideStatus == DriverRideStatus.ASSIGNED || rideStatus == DriverRideStatus.ACCEPT_REQUESTED) {
            return "ASSIGNED";
        }
        return "BUSY";
    }

    private boolean isActiveForBooking(DriverProfile profile) {
        return profile.getVerificationStatus() == DriverVerificationStatus.APPROVED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE
                && currentRideStatusName(profile) == null;
    }

    private String currentRideStatusName(DriverProfile profile) {
        return profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name();
    }

    private DriverLocationPayload toLocationPayload(DriverProfile profile) {
        return DriverLocationPayload.builder()
                .lat(profile.getCurrentLatitude())
                .lng(profile.getCurrentLongitude())
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
