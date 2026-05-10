package iuh.fit.payment_service.producer;

import iuh.fit.payment_service.config.KafkaConfig;
import iuh.fit.payment_service.dto.event.PaymentCompletedEvent;
import iuh.fit.payment_service.dto.event.PaymentFailedEvent;
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
        log.info("[Producer] Sending payment.completed event - txnId={}, rideId={}, amount={}",
                event.getTransactionId(), event.getRideId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                event.getTransactionId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.completed - txnId={}: {}",
                        event.getTransactionId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.completed sent - txnId={}, partition={}, offset={}",
                        event.getTransactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
        log.info("[Producer] Sending payment.failed event - txnId={}, rideId={}, reason={}",
                event.getTransactionId(), event.getRideId(), event.getFailureReason());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_FAILED,
                event.getTransactionId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.failed - txnId={}: {}",
                        event.getTransactionId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.failed sent - txnId={}, partition={}, offset={}",
                        event.getTransactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
