package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.DriverAcceptedEvent;
import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.event.DriverRejectedEvent;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.request.CompleteCurrentRideRequest;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateCurrentRideStatusRequest;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.entity.DriverAssignmentAction;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverRideCommandService {

    private static final String RIDE_ACCEPTED_TOPIC = "ride.accepted";
    private static final String RIDE_REJECTED_TOPIC = "ride.rejected";
    private static final String PENDING_RIDE_KEY_PREFIX = "driver:";
    private static final String PENDING_RIDE_KEY_SUFFIX = ":pending-ride";
    private static final String ASSIGNMENT_TIMEOUT_REASON = "ASSIGNMENT_TIMEOUT";

    private final DriverProfileRepository driverProfileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DriverStatusService driverStatusService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${driver.assignment.ttl-seconds:30}")
    private long assignmentTtlSeconds;

    @Transactional
    public void handleRideAssigned(RideAssignedEvent event) {
        String rideId = event.aggregateId();
        String driverId = normalize(event.getDriverId());
        if (rideId == null || rideId.isBlank() || driverId == null || driverId.isBlank()) {
            log.warn("Skip ride.assigned with missing rideId/driverId | rideId={} | driverId={}", rideId, driverId);
            return;
        }

        DriverProfile profile = driverProfileRepository.findByExternalUserId(driverId).orElse(null);
        if (profile == null) {
            log.warn("Assigned driver profile not found, rejecting assignment | rideId={} | driverId={}", rideId, driverId);
            publishDriverRejected(rideId, driverId, "Assigned driver profile not found");
            return;
        }

        if (isSamePendingAssignment(profile, rideId)) {
            writePendingRide(profile, event);
            log.info("Duplicate ride.assigned skipped | rideId={} | driverId={}", rideId, driverId);
            return;
        }

        if (!canReceiveAssignment(profile)) {
            log.warn("Driver cannot receive assignment, rejecting | rideId={} | driverId={} | availability={} | currentRideId={} | currentRideStatus={}",
                    rideId, driverId, profile.getAvailabilityStatus(), profile.getCurrentRideId(), profile.getCurrentRideStatus());
            publishDriverRejected(rideId, driverId, "Driver unavailable for assignment");
            return;
        }

        profile.setCurrentRideId(rideId);
        profile.setCurrentBookingId(hasText(event.getBookingId()) ? event.getBookingId().trim() : rideId);
        profile.setCurrentRideCustomerId(normalize(event.getCustomerId()));
        profile.setCurrentRideStatus(DriverRideStatus.ASSIGNED);
        profile.setCurrentRidePickup(normalize(event.getPickupAddress()));
        profile.setCurrentRideDestination(normalize(event.getDropoffAddress()));
        profile.setCurrentRidePickupLat(coordinate(event.getPickup(), "lat"));
        profile.setCurrentRidePickupLng(coordinate(event.getPickup(), "lng"));
        profile.setCurrentRideDropoffLat(coordinate(event.getDropoff(), "lat"));
        profile.setCurrentRideDropoffLng(coordinate(event.getDropoff(), "lng"));
        profile.setCurrentRideVehicleType(normalize(event.getVehicleType()));
        profile.setCurrentRidePaymentMethod(normalize(event.getPaymentMethod()));
        profile.setCurrentRideEstimatedFare(event.getEstimatedFare());
        profile.setCurrentRideRequestedAt(parseEventTimestamp(event.getTimestamp()));
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        writePendingRide(savedProfile, event);
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Current ride updated | rideId={} | bookingId={} | driverId={} | rideStatus={} | availability={}",
                rideId, savedProfile.getCurrentBookingId(), driverId,
                savedProfile.getCurrentRideStatus(), savedProfile.getAvailabilityStatus());
    }

    @Transactional
    public DriverCurrentRideResponse handleAssignment(String externalUserId, HandleDriverAssignmentRequest request) {
        DriverAssignmentAction action = DriverAssignmentAction.valueOf(request.getAction().trim().toUpperCase());
        return action == DriverAssignmentAction.REJECT
                ? rejectRide(externalUserId, request.getRideId())
                : acceptRide(externalUserId, request.getRideId());
    }

    @Transactional(noRollbackFor = AppException.class)
    public DriverCurrentRideResponse acceptRide(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensureDriverCanTakeCommand(profile);
        ensurePendingAssignment(profile, rideId);
        ensurePendingRideNotExpired(profile);

        profile.setCurrentRideStatus(DriverRideStatus.ACCEPTED);
        profile.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        publishDriverAccepted(savedProfile.getCurrentRideId(), savedProfile.getExternalUserId());
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse rejectRide(String externalUserId, String rideId) {
        DriverProfile profile = getRequiredProfile(externalUserId);
        ensurePendingAssignment(profile, rideId);

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        publishDriverRejected(rideId, savedProfile.getExternalUserId(), "Driver rejected assignment");
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse updateCurrentRideStatus(String externalUserId,
                                                              UpdateCurrentRideStatusRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);

        if (profile.getCurrentRideId() == null || profile.getCurrentRideStatus() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        DriverRideStatus requestedStatus;
        try {
            requestedStatus = DriverRideStatus.valueOf(request.getRideStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        DriverRideStatus current = profile.getCurrentRideStatus();

        // Allow idempotent calls: same status → same status is a no-op success
        if (requestedStatus == current) {
            log.info("Idempotent ride status update (no-op) | rideId={} | driverId={} | status={}",
                    profile.getCurrentRideId(), externalUserId, current);
            return toCurrentRideResponse(profile);
        }

        // Validate allowed transitions
        boolean allowed = switch (requestedStatus) {
            case EN_ROUTE_PICKUP -> current == DriverRideStatus.ACCEPTED;
            case ARRIVED_PICKUP  -> current == DriverRideStatus.EN_ROUTE_PICKUP
                                 || current == DriverRideStatus.ACCEPTED;
            case IN_PROGRESS     -> current == DriverRideStatus.ACCEPTED
                                 || current == DriverRideStatus.EN_ROUTE_PICKUP
                                 || current == DriverRideStatus.ARRIVED_PICKUP;
            default -> false;
        };

        if (!allowed) {
            log.warn("Illegal ride status transition | from={} | to={} | driverId={}",
                    current, requestedStatus, externalUserId);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        profile.setCurrentRideStatus(requestedStatus);
        if (request.getCurrentLatitude() != null) {
            profile.setCurrentLatitude(request.getCurrentLatitude());
        }
        if (request.getCurrentLongitude() != null) {
            profile.setCurrentLongitude(request.getCurrentLongitude());
        }
        profile.setLastOnlineAt(LocalDateTime.now());

        DriverProfile savedProfile = driverProfileRepository.save(profile);
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Ride status updated | rideId={} | driverId={} | status={}",
                savedProfile.getCurrentRideId(), externalUserId, requestedStatus);
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public DriverCurrentRideResponse completeCurrentRide(String externalUserId,
                                                         CompleteCurrentRideRequest request) {
        DriverProfile profile = getRequiredProfile(externalUserId);

        if (profile.getCurrentRideId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        String rideId = profile.getCurrentRideId();
        profile.setCurrentRideStatus(DriverRideStatus.COMPLETED);
        driverProfileRepository.save(profile);

        // Let the ride.completed Kafka event (from ride-service) do the full cleanup;
        // but also do a local cleanup so driver is immediately ONLINE again.
        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Ride completed locally | rideId={} | driverId={}", rideId, externalUserId);
        return toCurrentRideResponse(savedProfile);
    }

    @Transactional
    public void cleanupRide(String rideId, String driverId, String sourceTopic) {
        if (rideId == null || rideId.isBlank()) {
            log.warn("Skip driver cleanup without rideId | source={}", sourceTopic);
            return;
        }

        DriverProfile profile = resolveProfileForCleanup(rideId, driverId);
        if (profile == null) {
            log.info("No driver current ride to cleanup | rideId={} | source={}", rideId, sourceTopic);
            return;
        }

        clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        clearPendingRide(savedProfile.getExternalUserId());
        driverStatusService.writeDriverStatus(savedProfile);
        log.info("Driver ride state cleaned | rideId={} | driverId={} | source={}",
                rideId, savedProfile.getExternalUserId(), sourceTopic);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void cleanupExpiredAssignments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(assignmentTtl());
        for (DriverProfile profile : driverProfileRepository
                .findByCurrentRideStatusAndCurrentRideRequestedAtBefore(DriverRideStatus.ASSIGNED, cutoff)) {
            String rideId = profile.getCurrentRideId();
            String driverId = profile.getExternalUserId();
            log.warn("Assignment timeout detected | rideId={} | driverId={} | reason={}",
                    rideId, driverId, ASSIGNMENT_TIMEOUT_REASON);

            publishDriverRejected(rideId, driverId, ASSIGNMENT_TIMEOUT_REASON);
            log.info("Publishing ride.rejected | rideId={} | driverId={} | reason={}",
                    rideId, driverId, ASSIGNMENT_TIMEOUT_REASON);

            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            DriverProfile savedProfile = driverProfileRepository.save(profile);
            clearPendingRide(savedProfile.getExternalUserId());
            driverStatusService.writeDriverStatus(savedProfile);
            log.info("Driver returned to ONLINE | rideId={} | driverId={}",
                    rideId, savedProfile.getExternalUserId());
        }
    }

    private DriverProfile getRequiredProfile(String externalUserId) {
        return driverProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private void ensurePendingAssignment(DriverProfile profile, String rideId) {
        if (rideId == null || rideId.isBlank()
                || profile.getCurrentRideId() == null
                || !profile.getCurrentRideId().equals(rideId)
                || profile.getCurrentRideStatus() != DriverRideStatus.ASSIGNED) {
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
        if (profile.getCurrentRideStatus() != DriverRideStatus.ASSIGNED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ON_TRIP) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void clearCurrentRide(DriverProfile profile, DriverAvailabilityStatus nextAvailabilityStatus) {
        profile.setCurrentRideId(null);
        profile.setCurrentBookingId(null);
        profile.setCurrentRideCustomerId(null);
        profile.setCurrentRideStatus(null);
        profile.setCurrentRidePickup(null);
        profile.setCurrentRideDestination(null);
        profile.setCurrentRidePickupLat(null);
        profile.setCurrentRidePickupLng(null);
        profile.setCurrentRideDropoffLat(null);
        profile.setCurrentRideDropoffLng(null);
        profile.setCurrentRideVehicleType(null);
        profile.setCurrentRidePaymentMethod(null);
        profile.setCurrentRideEstimatedFare(null);
        profile.setCurrentRideRequestedAt(null);
        profile.setAvailabilityStatus(nextAvailabilityStatus);
    }

    private boolean canReceiveAssignment(DriverProfile profile) {
        return profile.getVerificationStatus() == DriverVerificationStatus.APPROVED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE
                && profile.getCurrentRideId() == null
                && profile.getCurrentRideStatus() == null;
    }

    private boolean isSamePendingAssignment(DriverProfile profile, String rideId) {
        return rideId.equals(profile.getCurrentRideId())
                && profile.getCurrentRideStatus() == DriverRideStatus.ASSIGNED;
    }

    private void ensurePendingRideNotExpired(DriverProfile profile) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(pendingRideKey(profile.getExternalUserId())))) {
            clearCurrentRide(profile, DriverAvailabilityStatus.ONLINE);
            driverProfileRepository.save(profile);
            driverStatusService.writeDriverStatus(profile);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private DriverProfile resolveProfileForCleanup(String rideId, String driverId) {
        String normalizedDriverId = normalize(driverId);
        if (normalizedDriverId != null && !normalizedDriverId.isBlank()) {
            return driverProfileRepository.findByExternalUserId(normalizedDriverId)
                    .filter(profile -> rideId.equals(profile.getCurrentRideId()))
                    .orElse(null);
        }
        return driverProfileRepository.findByCurrentRideId(rideId).orElse(null);
    }

    private void writePendingRide(DriverProfile profile, RideAssignedEvent event) {
        String rideId = event.aggregateId();
        String key = pendingRideKey(profile.getExternalUserId());
        Instant assignedAt = Instant.now();
        Instant expiredAt = assignedAt.plus(assignmentTtl());

        Map<String, String> pendingRide = new HashMap<>();
        pendingRide.put("rideId", rideId);
        pendingRide.put("bookingId", event.getBookingId() == null ? rideId : event.getBookingId());
        pendingRide.put("driverId", profile.getExternalUserId());
        pendingRide.put("status", DriverRideStatus.ASSIGNED.name());
        pendingRide.put("assignedAt", assignedAt.toString());
        pendingRide.put("expiredAt", expiredAt.toString());
        put(pendingRide, "customerId", event.getCustomerId());
        put(pendingRide, "pickupLocation", event.getPickupAddress());
        put(pendingRide, "dropoffLocation", event.getDropoffAddress());
        put(pendingRide, "pickupLat", coordinate(event.getPickup(), "lat"));
        put(pendingRide, "pickupLng", coordinate(event.getPickup(), "lng"));
        put(pendingRide, "dropoffLat", coordinate(event.getDropoff(), "lat"));
        put(pendingRide, "dropoffLng", coordinate(event.getDropoff(), "lng"));
        put(pendingRide, "vehicleType", event.getVehicleType());
        put(pendingRide, "paymentMethod", event.getPaymentMethod());
        if (event.getEstimatedFare() != null) {
            pendingRide.put("fare", event.getEstimatedFare().toPlainString());
        }

        stringRedisTemplate.opsForHash().putAll(key, pendingRide);
        stringRedisTemplate.expire(key, assignmentTtl().toSeconds(), TimeUnit.SECONDS);
    }

    private void clearPendingRide(String driverId) {
        stringRedisTemplate.delete(pendingRideKey(driverId));
    }

    private String pendingRideKey(String driverId) {
        return PENDING_RIDE_KEY_PREFIX + driverId + PENDING_RIDE_KEY_SUFFIX;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private BigDecimal coordinate(Map<String, Double> coordinates, String key) {
        if (coordinates == null) {
            return null;
        }
        Object value = coordinates.get(key);
        if (value == null) {
            if ("lat".equalsIgnoreCase(key)) {
                value = coordinates.get("latitude");
            } else if ("lng".equalsIgnoreCase(key)) {
                value = coordinates.get("longitude");
            }
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    private void put(Map<String, String> target, String key, Object value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }

    private LocalDateTime parseEventTimestamp(String timestamp) {
        if (!hasText(timestamp)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(timestamp.trim()), java.time.ZoneId.systemDefault());
        } catch (DateTimeParseException ex) {
            log.warn("Invalid ride.assigned timestamp, using current time | timestamp={}", timestamp);
            return LocalDateTime.now();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Duration assignmentTtl() {
        return Duration.ofSeconds(Math.max(1, assignmentTtlSeconds));
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
                .currentLocation(toLocationPayload(profile.getCurrentLatitude(), profile.getCurrentLongitude()))
                .build();
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

    private void publishDriverAccepted(String rideId, String driverId) {
        kafkaTemplate.send(
                RIDE_ACCEPTED_TOPIC,
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
                RIDE_REJECTED_TOPIC,
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
