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
        String kafkaKey = event.getRideId() != null ? event.getRideId() : event.getBookingId();
        
        log.info("[Producer] Sending payment.completed - rideId={}, bookingId={}, amount={}",
                event.getRideId(), event.getBookingId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                kafkaKey,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.completed - rideId={}: {}",
                        kafkaKey, ex.getMessage());
            } else {
                log.info("[Producer] payment.completed sent - rideId={}, partition={}, offset={}",
                        kafkaKey,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
        String kafkaKey = event.getRideId() != null ? event.getRideId() : event.getBookingId();
        
        log.info("[Producer] Sending payment.failed - rideId={}, bookingId={}, reason={}",
                event.getRideId(), event.getBookingId(), event.getReason());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_FAILED,
                kafkaKey,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.failed - rideId={}: {}",
                        kafkaKey, ex.getMessage());
            } else {
                log.info("[Producer] payment.failed sent - rideId={}, partition={}, offset={}",
                        kafkaKey,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendPaymentRefunded(PaymentRefundedEvent event) {
        String kafkaKey = event.getBookingId();
        
        log.info("[Producer] Sending payment.refunded - bookingId={}, amount={}",
                event.getBookingId(), event.getAmount());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_PAYMENT_REFUNDED,
                kafkaKey,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Producer] Failed to send payment.refunded - bookingId={}: {}",
                        kafkaKey, ex.getMessage());
            } else {
                log.info("[Producer] payment.refunded sent - bookingId={}, partition={}, offset={}",
                        kafkaKey,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
