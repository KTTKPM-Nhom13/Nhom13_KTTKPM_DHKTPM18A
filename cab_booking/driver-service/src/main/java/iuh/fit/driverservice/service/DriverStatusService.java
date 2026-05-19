package iuh.fit.driverservice.service;

import iuh.fit.driverservice.dto.event.DriverLocationPayload;
import iuh.fit.driverservice.dto.event.DriverStatusEvent;
import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverStatusService {

    private static final String DRIVER_STATUS_CHANGED_TOPIC = "driver.status.changed";
    private static final String DRIVER_STATUS_PREFIX = "driver:status:";
    private static final String DRIVER_VEHICLE_TYPE_PREFIX = "driver:vehicleType:";
    private static final String DRIVER_PROFILE_PREFIX = "driver:profile:";
    private static final Duration ASSIGNED_STATUS_TTL = Duration.ofMinutes(5);
    private static final Duration BUSY_STATUS_TTL = Duration.ofHours(12);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public void writeDriverStatus(DriverProfile profile) {
        String status = redisStatusFor(profile);
        String driverId = profile.getExternalUserId();
        String key = DRIVER_STATUS_PREFIX + driverId;
        if ("ASSIGNED".equals(status)) {
            stringRedisTemplate.opsForValue().set(key, status, ASSIGNED_STATUS_TTL);
        } else if ("BUSY".equals(status)) {
            stringRedisTemplate.opsForValue().set(key, status, BUSY_STATUS_TTL);
        } else {
            stringRedisTemplate.opsForValue().set(key, status);
        }
        writeDriverVehicleTypeMetadata(profile);
    }

    public void publishDriverStatusChanged(DriverProfile profile, String rideId, String rideStatus) {
        kafkaTemplate.send(
                DRIVER_STATUS_CHANGED_TOPIC,
                DriverStatusEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .type(DriverStatusEvent.EVENT_TYPE)
                        .driverId(profile.getExternalUserId())
                        .availabilityStatus(profile.getAvailabilityStatus().name())
                        .activeForBooking(isActiveForBooking(profile))
                        .vehicleType(profile.getVehicleType() == null ? null : profile.getVehicleType().name())
                        .rideId(rideId)
                        .rideStatus(rideStatus)
                        .currentLocation(toLocationPayload(profile))
                        .timestamp(Instant.now().toString())
                        .build());
    }

    public boolean isActiveForBooking(DriverProfile profile) {
        return profile.getVerificationStatus() == DriverVerificationStatus.APPROVED
                && profile.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE
                && currentRideStatusName(profile) == null;
    }

    public String currentRideStatusName(DriverProfile profile) {
        return profile.getCurrentRideStatus() == null ? null : profile.getCurrentRideStatus().name();
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

    private void writeDriverVehicleTypeMetadata(DriverProfile profile) {
        if (profile.getVehicleType() == null) {
            return;
        }

        String driverId = profile.getExternalUserId();
        String vehicleType = profile.getVehicleType().name();
        stringRedisTemplate.opsForValue().set(DRIVER_VEHICLE_TYPE_PREFIX + driverId, vehicleType);
        stringRedisTemplate.opsForHash().put(DRIVER_PROFILE_PREFIX + driverId, "vehicleType", vehicleType);
    }

    private DriverLocationPayload toLocationPayload(DriverProfile profile) {
        return DriverLocationPayload.builder()
                .lat(profile.getCurrentLatitude())
                .lng(profile.getCurrentLongitude())
                .build();
    }
}
