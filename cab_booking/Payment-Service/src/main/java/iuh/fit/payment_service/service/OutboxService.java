package iuh.fit.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.entity.OutboxEvent;
import iuh.fit.payment_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void saveOutboxEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .build();
            outboxRepository.save(event);
            log.debug("[Outbox] Event saved - aggregateType={}, aggregateId={}, eventType={}",
                    aggregateType, aggregateId, eventType);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] Failed to serialize payload - aggregateType={}, aggregateId={}, eventType={}: {}",
                    aggregateType, aggregateId, eventType, e.getMessage());
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOutboxEventInTx(String aggregateType, String aggregateId, String eventType, Object payload) {
        saveOutboxEvent(aggregateType, aggregateId, eventType, payload);
    }
}
