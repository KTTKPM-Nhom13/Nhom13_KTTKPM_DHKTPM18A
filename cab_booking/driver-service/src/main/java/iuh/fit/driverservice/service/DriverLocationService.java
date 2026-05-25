package iuh.fit.driverservice.service;

import iuh.fit.driverservice.entity.DriverProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Manages the {@code driver:available:locations} Redis GEO key.
 *
 * <p>This key contains ONLY drivers who are ONLINE and have no active ride.
 * It is the single source of truth for matching-service to find nearby available drivers.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>GEOADD when driver becomes ONLINE (with coordinates)</li>
 *   <li>ZREM when driver goes OFFLINE or ON_TRIP</li>
 *   <li>GEOADD when driver returns to ONLINE after ride ends</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private static final String AVAILABLE_LOCATIONS_KEY = "driver:available:locations";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Add driver to the available-location GEO set.
     * Called when driver goes ONLINE with valid coordinates.
     *
     * @param driverId the driver's external user ID
     * @param lat      latitude
     * @param lng      longitude
     */
    public void addToAvailableGeo(String driverId, double lat, double lng) {
        try {
            Long added = stringRedisTemplate.opsForGeo()
                    .add(AVAILABLE_LOCATIONS_KEY, new Point(lng, lat), driverId);
            log.info("[DriverLocationService] GEOADD driver:available:locations | driverId={} | lat={} | lng={} | result={}",
                    driverId, lat, lng, added);
        } catch (Exception ex) {
            log.error("[DriverLocationService] Failed to GEOADD driver:available:locations | driverId={} | error={}",
                    driverId, ex.getMessage(), ex);
        }
    }

    /**
     * Remove driver from the available-location GEO set.
     * Called when driver goes OFFLINE or ON_TRIP.
     *
     * @param driverId the driver's external user ID
     */
    public void removeFromAvailableGeo(String driverId) {
        try {
            Long removed = stringRedisTemplate.opsForZSet()
                    .remove(AVAILABLE_LOCATIONS_KEY, driverId);
            log.info("[DriverLocationService] ZREM driver:available:locations | driverId={} | removed={}",
                    driverId, removed);
        } catch (Exception ex) {
            log.error("[DriverLocationService] Failed to ZREM driver:available:locations | driverId={} | error={}",
                    driverId, ex.getMessage(), ex);
        }
    }

    /**
     * Convenience method: add driver to available GEO using coordinates from their profile.
     * Skips silently if coordinates are null.
     *
     * @param profile the driver profile with currentLatitude/currentLongitude
     */
    public void addFromProfile(DriverProfile profile) {
        if (profile == null) {
            return;
        }
        BigDecimal lat = profile.getCurrentLatitude();
        BigDecimal lng = profile.getCurrentLongitude();
        if (lat == null || lng == null) {
            log.warn("[DriverLocationService] Cannot add to available GEO — no coordinates | driverId={}",
                    profile.getExternalUserId());
            return;
        }
        addToAvailableGeo(profile.getExternalUserId(), lat.doubleValue(), lng.doubleValue());
    }
}
