package iuh.fit.pricing_service.consumer;

import iuh.fit.pricing_service.config.KafkaConfig;
import iuh.fit.pricing_service.service.SurgePricingService;
import iuh.fit.pricing_service.service.ZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandSupplyConsumer {

    private static final String TOPIC_DEMAND_SUPPLY = "demand.supply.updated";
    private static final String TOPIC_RIDE_CREATED = "ride.created";
    private static final String TOPIC_RIDE_CANCELLED = "ride.cancelled";
    private static final String TOPIC_RIDE_FINISHED = "ride.finished";
    private static final String TOPIC_RIDE_COMPLETED = "ride.completed";
    private static final String TOPIC_DRIVER_STATUS_UPDATED = "driver.status.updated";
    private static final String TOPIC_DRIVER_STATUS_CHANGED = "driver.status.changed";
    private static final String TOPIC_DRIVER_LOCATION_UPDATED = "driver.location.updated";
    private static final String GROUP_ID = "pricing-demand-supply-group";

    private final SurgePricingService surgePricingService;
    private final ZoneService zoneService;

    @KafkaListener(
            topics = {
                    TOPIC_DEMAND_SUPPLY,
                    TOPIC_RIDE_CREATED,
                    TOPIC_RIDE_CANCELLED,
                    TOPIC_RIDE_FINISHED,
                    TOPIC_RIDE_COMPLETED,
                    TOPIC_DRIVER_STATUS_UPDATED,
                    TOPIC_DRIVER_STATUS_CHANGED,
                    TOPIC_DRIVER_LOCATION_UPDATED
            },
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDemandSupplyEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("Received pricing metric event - Key: {}, Partition: {}, Offset: {}", key, partition, offset);
        log.debug("Event payload: {}", event);

        try {
            processDemandSupplyEvent(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed pricing metric event - Key: {}", key);
        } catch (Exception e) {
            log.error("Error processing pricing metric event - Key: {}, Error: {}", key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void processDemandSupplyEvent(Map<String, Object> event) {
        String eventType = extractString(event, "type");
        if (eventType == null) {
            eventType = extractString(event, "eventType");
        }

        String zoneId = extractString(event, "zoneId");
        Integer activeDrivers = extractInteger(event, "activeDrivers");
        Integer pendingRides = extractInteger(event, "pendingRides");

        if (zoneId != null && activeDrivers != null && pendingRides != null) {
            surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
            log.info("Cached demand/supply metrics - zone: {}, drivers: {}, rides: {}",
                    zoneId, activeDrivers, pendingRides);
            return;
        }

        if ("RideCreated".equalsIgnoreCase(eventType)) {
            zoneId = extractZoneFromLocation(event, "pickup");
            String rideId = extractString(event, "rideId");
            if (zoneId != null) {
                surgePricingService.rememberRideZone(rideId, zoneId);
                surgePricingService.incrementZoneMetrics(zoneId, 0, 1);
            }
            return;
        }

        if ("RIDE_CANCELLED".equalsIgnoreCase(eventType)
                || "RideFinished".equalsIgnoreCase(eventType)
                || "RideCompleted".equalsIgnoreCase(eventType)) {
            String rideId = extractString(event, "rideId");
            zoneId = extractString(event, "zoneId");
            if (zoneId == null) {
                zoneId = surgePricingService.getRememberedRideZone(rideId).orElse(null);
            }
            if (zoneId != null) {
                surgePricingService.incrementZoneMetrics(zoneId, 0, -1);
                surgePricingService.forgetRideZone(rideId);
            }
            return;
        }

        if ("DriverStatusChanged".equalsIgnoreCase(eventType)) {
            zoneId = extractZoneFromLocation(event, "currentLocation");
            Boolean activeForBooking = extractBoolean(event, "activeForBooking");
            String driverId = extractString(event, "driverId");
            if (activeForBooking != null) {
                if (activeForBooking && zoneId != null) {
                    surgePricingService.rememberDriverZone(driverId, zoneId);
                    surgePricingService.incrementZoneMetrics(zoneId, 1, 0);
                } else {
                    String previousZone = surgePricingService.getRememberedDriverZone(driverId).orElse(zoneId);
                    if (previousZone != null) {
                        surgePricingService.incrementZoneMetrics(previousZone, -1, 0);
                    }
                    surgePricingService.forgetDriverZone(driverId);
                }
            }
            return;
        }

        if (eventType == null && event.containsKey("driverId") && event.containsKey("lat") && event.containsKey("lng")) {
            String driverId = extractString(event, "driverId");
            Double lat = extractDoubleValue(event.get("lat"));
            Double lng = extractDoubleValue(event.get("lng"));
            if (driverId != null && lat != null && lng != null) {
                String newZone = zoneService.determineZone(lat, lng);
                String oldZone = surgePricingService.getRememberedDriverZone(driverId).orElse(null);
                if (oldZone != null && !oldZone.equals(newZone)) {
                    surgePricingService.incrementZoneMetrics(oldZone, -1, 0);
                    surgePricingService.incrementZoneMetrics(newZone, 1, 0);
                    surgePricingService.rememberDriverZone(driverId, newZone);
                }
            }
            return;
        }

        if (zoneId == null || zoneId.isBlank()) {
            log.debug("Pricing metric event has no usable zoneId or supported event type, skipping");
            return;
        }

        if (activeDrivers == null || pendingRides == null) {
            log.warn("Active drivers or pending rides not provided in demand.supply event");
            return;
        }

        surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
    }

    private String extractString(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return (value != null) ? value.toString() : null;
    }

    private Integer extractInteger(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer for key {}: {}", key, value);
            return null;
        }
    }

    private Boolean extractBoolean(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private String extractZoneFromLocation(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (!(value instanceof Map)) {
            return null;
        }
        Map<Object, Object> location = (Map<Object, Object>) value;
        Double lat = extractDouble(location, "lat");
        Double lng = extractDouble(location, "lng");
        if (lat == null || lng == null) {
            return null;
        }
        return zoneService.determineZone(lat, lng);
    }

    private Double extractDouble(Map<Object, Object> values, String key) {
        return extractDoubleValue(values.get(key));
    }

    private Double extractDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
