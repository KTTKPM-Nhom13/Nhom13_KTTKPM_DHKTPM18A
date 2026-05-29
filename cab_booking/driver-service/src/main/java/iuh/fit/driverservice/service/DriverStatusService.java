package iuh.fit.driverservice.service;

import iuh.fit.driverservice.entity.DriverAvailabilityStatus;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.entity.DriverRideStatus;
import iuh.fit.driverservice.entity.DriverVerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverStatusService {

    private static final String DRIVER_STATUS_PREFIX = "driver:status:";
    private static final String DRIVER_VEHICLE_TYPE_PREFIX = "driver:vehicleType:";
    private static final String DRIVER_PROFILE_PREFIX = "driver:profile:";
    private static final String DRIVER_LOCK_PREFIX = "driver:lock:";
    private static final Duration ASSIGNED_STATUS_TTL = Duration.ofMinutes(5);
    private static final Duration BUSY_STATUS_TTL = Duration.ofHours(12);

    private final StringRedisTemplate stringRedisTemplate;

    public void writeDriverStatus(DriverProfile profile) {
        String status = redisStatusFor(profile);
        String driverId = profile.getExternalUserId();

        // When driver goes OFFLINE, purge ALL Redis state to prevent stale GEO matching.
        // This is critical: if a driver was online as BIKE, logs out, then logs in as CAR4,
        // the old BIKE GEO/vehicleType entries would cause incorrect matching.
        if ("OFFLINE".equals(status)) {
            clearAllDriverRedisKeys(driverId);
            return;
        }

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

    /**
     * Purges ALL Redis keys associated with a driver.
     * Called when a driver goes OFFLINE (logout or explicit offline toggle).
     * This prevents stale GEO/vehicleType data from persisting across sessions
     * where the driver may switch vehicle types.
     */
    public void clearAllDriverRedisKeys(String driverId) {
        try {
            stringRedisTemplate.delete(DRIVER_STATUS_PREFIX + driverId);
            stringRedisTemplate.delete(DRIVER_VEHICLE_TYPE_PREFIX + driverId);
            stringRedisTemplate.delete(DRIVER_PROFILE_PREFIX + driverId);
            stringRedisTemplate.delete(DRIVER_LOCK_PREFIX + driverId);
            log.info("[DriverStatusService] Purged ALL Redis keys for OFFLINE driver | driverId={}", driverId);
        } catch (Exception ex) {
            log.error("[DriverStatusService] Failed to purge Redis keys for driver {} | error={}",
                    driverId, ex.getMessage(), ex);
        }
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

}
