package iuh.fit.pricing_service.consumer;

import iuh.fit.pricing_service.config.KafkaConfig;
import iuh.fit.pricing_service.service.PricingService;
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
public class RideCreatedConsumer {

    private final PricingService pricingService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDE_CREATED,
            groupId = "pricing-ride-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRideCreatedEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("Received ride.created event - Key: {}, Partition: {}, Offset: {}", key, partition, offset);
        log.debug("Event payload: {}", event);

        try {
            processRideCreatedEvent(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed ride.created event - Key: {}", key);
        } catch (Exception e) {
            log.error("Error processing ride.created event - Key: {}, Error: {}", key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void processRideCreatedEvent(Map<String, Object> event) {
        String rideId = extractString(event, "rideId");
        String userId = extractString(event, "customerId");
        String vehicleType = extractString(event, "vehicleType");

        Map<String, Object> pickupMap = extractMap(event, "pickup");
        Map<String, Object> dropoffMap = extractMap(event, "dropoff");

        Double pickupLat = extractDoubleFromMap(pickupMap, "lat");
        Double pickupLng = extractDoubleFromMap(pickupMap, "lng");
        Double dropoffLat = extractDoubleFromMap(dropoffMap, "lat");
        Double dropoffLng = extractDoubleFromMap(dropoffMap, "lng");

        Double distance = extractDouble(event, "distance");
        Integer duration = extractInteger(event, "durationMinutes");

        log.info("Processing ride.created - rideId: {}, userId: {}, vehicleType: {}, distance: {} km, duration: {} min",
                rideId, userId, vehicleType, distance, duration);

        if (rideId == null || rideId.isBlank()) {
            log.warn("Received ride.created event with null or blank rideId, skipping");
            return;
        }

        String pickupZone = extractString(event, "pickupZone");
        String dropoffZone = extractString(event, "dropoffZone");

        if (pickupZone == null || pickupZone.isBlank()) {
            pickupZone = determineZone(pickupLat, pickupLng);
            log.debug("Pickup zone not provided, determined: {}", pickupZone);
        }

        if (dropoffZone == null || dropoffZone.isBlank()) {
            dropoffZone = determineZone(dropoffLat, dropoffLng);
            log.debug("Dropoff zone not provided, determined: {}", dropoffZone);
        }

        if (distance == null || distance <= 0) {
            distance = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
            log.debug("Distance not provided, calculated: {} km", distance);
        }

        if (duration == null || duration <= 0) {
            duration = estimateDuration(distance);
            log.debug("Duration not provided, estimated: {} min", duration);
        }

        String normalizedVehicleType = (vehicleType != null) ? vehicleType.toUpperCase() : "ECONOMY";

        pricingService.applyFinalPricing(rideId, pickupZone, dropoffZone, normalizedVehicleType, distance, duration);

        log.info("Final pricing applied for ride: {}", rideId);
    }

    private String extractString(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value == null) return null;
        return value.toString();
    }

    private Double extractDouble(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double for key {}: {}", key, value);
            return null;
        }
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private Double extractDoubleFromMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double from map for key {}: {}", key, value);
            return null;
        }
    }

    private String determineZone(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "Z0000";
        }
        int gridSize = 1;
        int latZone = (int) Math.floor(lat * gridSize);
        int lngZone = (int) Math.floor(lng * gridSize);
        return String.format("Z%02d%02d", latZone + 50, lngZone + 100);
    }

    private double calculateDistance(Double pickupLat, Double pickupLng, Double dropoffLat, Double dropoffLng) {
        if (pickupLat == null || pickupLng == null || dropoffLat == null || dropoffLng == null) {
            return 0.0;
        }
        final double R = 6371.0;
        double dLat = Math.toRadians(dropoffLat - pickupLat);
        double dLng = Math.toRadians(dropoffLng - pickupLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(dropoffLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 100.0) / 100.0;
    }

    private int estimateDuration(double distanceKm) {
        double avgSpeedKmh = 30.0;
        return Math.max((int) Math.ceil((distanceKm / avgSpeedKmh) * 60), 1);
    }
}
