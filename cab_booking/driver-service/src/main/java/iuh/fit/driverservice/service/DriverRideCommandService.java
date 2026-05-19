package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.DriverAcceptedEvent;
import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.event.DriverRejectedEvent;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.entity.DriverAssignmentAction;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverRideCommandService {

    private static final String DRIVER_ACCEPTED_TOPIC = "driver.accepted";
    private static final String DRIVER_REJECTED_TOPIC = "driver.rejected";

    private final DriverProfileRepository driverProfileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DriverStatusService driverStatusService;

    @Transactional
    public DriverCurrentRideResponse handleAssignment(String externalUserId, HandleDriverAssignmentRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        DriverAssignmentAction action = DriverAssignmentAction.valueOf(request.getAction().trim().toUpperCase());
        String requestedRideId = request.getRideId();

        if (action == DriverAssignmentAction.REJECT) {
            ensureRideMatchesCurrentAssignment(profile, requestedRideId);
            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            DriverProfile savedProfile = driverProfileRepository.save(profile);
            driverStatusService.writeDriverStatus(savedProfile);
            publishDriverRejected(requestedRideId, savedProfile.getExternalUserId(), "Driver rejected assignment");
            driverStatusService.publishDriverStatusChanged(savedProfile, requestedRideId, "REJECTED");
            return toCurrentRideResponse(savedProfile);
        }

        ensureDriverCanTakeCommand(profile);
        ensureRideMatchesCurrentAssignment(profile, requestedRideId);

        profile.setCurrentRideId(requestedRideId);
        profile.setCurrentRideStatus(null);
        profile.setCurrentRidePickup(request.getPickupAddress());
        profile.setCurrentRideDestination(request.getDestinationAddress());
        profile.setCurrentRideRequestedAt(
                request.getRequestedAt() == null ? LocalDateTime.now() : request.getRequestedAt());
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        driverStatusService.writeDriverStatus(savedProfile);
        publishDriverAccepted(savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        driverStatusService.publishDriverStatusChanged(savedProfile, savedProfile.getCurrentRideId(), "ACCEPTED");
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

    private DriverProfile getRequiredProfile(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private void ensureRideMatchesCurrentAssignment(DriverProfile profile, String rideId) {
        if (profile.getCurrentRideId() != null && !profile.getCurrentRideId().equals(rideId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void ensureDriverCanTakeCommand(DriverProfile profile) {
        if (profile.getVerificationStatus() != DriverVerificationStatus.APPROVED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (profile.getAvailabilityStatus() == DriverAvailabilityStatus.OFFLINE) {
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

    private DriverCurrentRideResponse toCurrentRideResponse(DriverProfile profile) {
        return DriverCurrentRideResponse.builder()
                .rideId(profile.getCurrentRideId())
                .rideStatus(profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name())
                .pickupAddress(profile.getCurrentRidePickup())
                .destinationAddress(profile.getCurrentRideDestination())
                .requestedAt(profile.getCurrentRideRequestedAt())
                .driverAvailabilityStatus(profile.getAvailabilityStatus().name())
                .currentLocation(DriverLocationPayload.builder()
                        .lat(profile.getCurrentLatitude())
                        .lng(profile.getCurrentLongitude())
                        .build())
                .build();
    }

    private void publishDriverAccepted(String rideId, String driverId) {
        kafkaTemplate.send(
                DRIVER_ACCEPTED_TOPIC,
                rideId,
                DriverAcceptedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(DriverAcceptedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .bookingId(rideId)
                        .driverId(driverId)
                        .timestamp(Instant.now().toString())
                        .build());
    }

    private void publishDriverRejected(String rideId, String driverId, String reason) {
        kafkaTemplate.send(
                DRIVER_REJECTED_TOPIC,
                rideId,
                DriverRejectedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(DriverRejectedEvent.EVENT_TYPE)
                        .rideId(rideId)
                        .bookingId(rideId)
                        .driverId(driverId)
                        .reason(reason)
                        .timestamp(Instant.now().toString())
                        .build());
    }

}
