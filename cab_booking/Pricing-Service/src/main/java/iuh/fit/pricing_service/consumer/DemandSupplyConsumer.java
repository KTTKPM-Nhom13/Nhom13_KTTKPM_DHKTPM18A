package iuh.fit.pricing_service.consumer;

import iuh.fit.pricing_service.config.KafkaConfig;
import iuh.fit.pricing_service.service.SurgePricingService;
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
    private static final String GROUP_ID = "pricing-demand-supply-group";

    private final SurgePricingService surgePricingService;

    @KafkaListener(
            topics = TOPIC_DEMAND_SUPPLY,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDemandSupplyEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("Received demand.supply.updated event - Key: {}, Partition: {}, Offset: {}", key, partition, offset);
        log.debug("Event payload: {}", event);

        try {
            processDemandSupplyEvent(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed demand.supply.updated event - Key: {}", key);
        } catch (Exception e) {
            log.error("Error processing demand.supply.updated event - Key: {}, Error: {}", key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void processDemandSupplyEvent(Map<String, Object> event) {
        String zoneId = extractString(event, "zoneId");
        Integer activeDrivers = extractInteger(event, "activeDrivers");
        Integer pendingRides = extractInteger(event, "pendingRides");
        if (zoneId == null || zoneId.isBlank()) {
            log.warn("Received demand.supply event with null or blank zoneId, skipping");
            return;
        }

        if (activeDrivers == null || pendingRides == null) {
            log.warn("Active drivers or pending rides not provided in demand.supply event");
            return;
        }

        surgePricingService.updateCurrentZoneMetrics(zoneId, activeDrivers, pendingRides);
        log.info("Cached demand/supply metrics - zone: {}, drivers: {}, rides: {}",
                zoneId, activeDrivers, pendingRides);
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

}
