package iuh.fit.pricing_service.producer;

import iuh.fit.pricing_service.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class SurgeEventProducer {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    private static final String SCHEMA_VERSION = "1.0.0";

    public void publishSurgeUpdate(String zoneId, BigDecimal surgeMultiplier) {
        String messageKey = zoneId;
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", eventId);
        payload.put("zone_id", zoneId);
        payload.put("surge_multiplier", surgeMultiplier.doubleValue());
        payload.put("timestamp", Instant.now().toString());
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", "SURGE_UPDATED");

        log.info("Publishing surge update event - zone: {}, multiplier: {}, eventId: {}",
                zoneId, surgeMultiplier, eventId);

        try {
            CompletableFuture<SendResult<String, Map<String, Object>>> future =
                    kafkaTemplate.send(KafkaConfig.TOPIC_PRICING_SURGE_UPDATED, messageKey, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish surge update event for zone {}: {}",
                            zoneId, ex.getMessage(), ex);
                } else {
                    log.info("Surge update event published successfully - zone: {}, partition: {}, offset: {}",
                            zoneId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error sending surge update event to Kafka for zone {}: {}", zoneId, e.getMessage(), e);
        }
    }

    public void publishSurgeAlert(String zoneId, BigDecimal surgeMultiplier, String alertType) {
        String messageKey = zoneId;
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", eventId);
        payload.put("zone_id", zoneId);
        payload.put("surge_multiplier", surgeMultiplier.doubleValue());
        payload.put("timestamp", Instant.now().toString());
        payload.put("schema_version", SCHEMA_VERSION);
        payload.put("event_type", "PRICING_ALERT");
        payload.put("alert_type", alertType);

        log.info("Publishing surge alert - zone: {}, multiplier: {}, alertType: {}",
                zoneId, surgeMultiplier, alertType);

        try {
            kafkaTemplate.send(KafkaConfig.TOPIC_PRICING_SURGE_UPDATED, messageKey, payload);
            log.debug("Surge alert published for zone {}", zoneId);
        } catch (Exception e) {
            log.error("Failed to publish surge alert for zone {}: {}", zoneId, e.getMessage(), e);
        }
    }
}
