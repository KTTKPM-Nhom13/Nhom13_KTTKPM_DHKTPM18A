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
        log.info("[Producer] Sending payment.completed - bookingId={}, amount={}",
                event.getBookingId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                event.getBookingId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.completed - bookingId={}: {}",
                        event.getBookingId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.completed sent - bookingId={}, partition={}, offset={}",
                        event.getBookingId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
        log.info("[Producer] Sending payment.failed - bookingId={}, reason={}",
                event.getBookingId(), event.getReason());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_FAILED,
                event.getBookingId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.failed - bookingId={}: {}",
                        event.getBookingId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.failed sent - bookingId={}, partition={}, offset={}",
                        event.getBookingId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentRefunded(PaymentRefundedEvent event) {
        log.info("[Producer] Sending payment.refunded - bookingId={}, amount={}",
                event.getBookingId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_REFUNDED,
                event.getBookingId(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.refunded - bookingId={}: {}",
                        event.getBookingId(), ex.getMessage());
            } else {
                log.info("[Producer] payment.refunded sent - bookingId={}, partition={}, offset={}",
                        event.getBookingId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
