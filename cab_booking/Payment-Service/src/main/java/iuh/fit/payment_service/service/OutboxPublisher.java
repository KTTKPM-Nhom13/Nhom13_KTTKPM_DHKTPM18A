package iuh.fit.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.KafkaConfig;
import iuh.fit.payment_service.entity.OutboxEvent;
import iuh.fit.payment_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:1000}", initialDelayString = "${app.outbox.publish-initial-delay-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEventsWithLimit(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[OutboxPublisher] Processing {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEventSync(event);
                markEventSent(event.getId());
            } catch (Exception e) {
                log.error("[OutboxPublisher] Failed to publish event id={}: {}",
                        event.getId(), e.getMessage());
                markEventFailed(event, e.getMessage());
            }
        }
    }

    @Transactional
    public void publishEventSync(OutboxEvent event) {
        String topic = resolveTopic(event.getEventType());
        String key = event.getAggregateId();

        Object payload;
        try {
            payload = objectMapper.readValue(event.getPayload(), Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[OutboxPublisher] Failed to deserialize payload for event id={}: {}",
                    event.getId(), e.getMessage());
            markEventFailed(event, "Deserialization failed: " + e.getMessage());
            return;
        }

        kafkaTemplate.send(topic, key, payload);
        log.info("[OutboxPublisher] Event published - id={}, topic={}, eventType={}",
                event.getId(), topic, event.getEventType());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventSent(UUID eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxEvent.OutboxStatus.SENT);
            outboxRepository.save(event);
        });
    }

    @Transactional
    public void markEventFailed(OutboxEvent event, String error) {
        event.markFailed(error);
        outboxRepository.save(event);
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PAYMENT_COMPLETED" -> KafkaConfig.TOPIC_PAYMENT_COMPLETED;
            case "PAYMENT_FAILED" -> KafkaConfig.TOPIC_PAYMENT_FAILED;
            case "PAYMENT_REFUNDED" -> KafkaConfig.TOPIC_PAYMENT_REFUNDED;
            default -> KafkaConfig.TOPIC_PAYMENT_INITIATED;
        };
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupOldSentEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteOldSentEvents(cutoff);
        if (deleted > 0) {
            log.info("[OutboxPublisher] Cleaned up {} old SENT events", deleted);
        }
    }
}
