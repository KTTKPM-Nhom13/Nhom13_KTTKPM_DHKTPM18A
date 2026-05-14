package iuh.fit.payment_service.producer;

import iuh.fit.payment_service.config.KafkaConfig;
import iuh.fit.payment_service.dto.event.PaymentCompletedEvent;
import iuh.fit.payment_service.dto.event.PaymentFailedEvent;
import iuh.fit.payment_service.dto.event.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[Producer] Sending payment.completed - rideId={}, amount={}",
            event.getRideId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_COMPLETED,
            event.getRideId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.completed - rideId={}: {}",
                    event.getRideId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.completed sent - rideId={}, partition={}, offset={}",
                    event.getRideId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
            log.info("[Producer] Sending payment.failed - rideId={}, reason={}",
                event.getRideId(), event.getReason());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_FAILED,
            event.getRideId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.failed - rideId={}: {}",
                    event.getRideId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.failed sent - rideId={}, partition={}, offset={}",
                    event.getRideId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentRefunded(PaymentRefundedEvent event) {
            log.info("[Producer] Sending payment.refunded - rideId={}, amount={}",
                event.getRideId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_REFUNDED,
            event.getRideId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.refunded - rideId={}: {}",
                        event.getRideId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.refunded sent - rideId={}, partition={}, offset={}",
                        event.getRideId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
