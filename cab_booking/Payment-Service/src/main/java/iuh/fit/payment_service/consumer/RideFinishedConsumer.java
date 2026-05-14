package iuh.fit.payment_service.consumer;

import iuh.fit.payment_service.dto.event.RideFinishedEvent;
import iuh.fit.payment_service.dto.request.ChargePaymentRequest;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.service.PaymentSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideFinishedConsumer {

    private final PaymentSagaService paymentSagaService;

    @KafkaListener(
            topics = "ride.finished",
            groupId = "payment-ride-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRideFinishedEvent(
            @Payload RideFinishedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("[Consumer] Received ride.finished - key={}, partition={}, offset={}, eventType={}, rideId={}, finalFare={}",
                key, partition, offset, event.getEventType(), event.getRideId(), event.getFinalFare());

        try {
            processPaymentFromRideFinished(event);
            acknowledgment.acknowledge();
            log.info("[Consumer] Successfully processed ride.finished - key={}", key);
        } catch (Exception e) {
            log.error("[Consumer] Error processing ride.finished event - key={}", key, e);
            acknowledgment.acknowledge();
        }
    }

    private void processPaymentFromRideFinished(RideFinishedEvent event) {
        log.info("[Consumer] Initiating automatic payment for rideId={}", event.getRideId());

        PaymentMethod method = PaymentMethod.CASH;
        if (event.getPaymentMethod() != null) {
            try {
                method = PaymentMethod.valueOf(event.getPaymentMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] Unknown payment method '{}', defaulting to CASH", event.getPaymentMethod());
            }
        }

        BigDecimal amount = event.getFinalFare();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[Consumer] Skipping payment - invalid finalFare {} for rideId={}", amount, event.getRideId());
            return;
        }

        ChargePaymentRequest chargeRequest = ChargePaymentRequest.builder()
                .bookingId(event.getRideId())
                .customerId(event.getCustomerId())
                .amount(amount)
                .currency("VND")
                .paymentMethod(method)
                .description("Auto-payment for ride " + event.getRideId())
                .idempotencyKey("ride-finished-" + event.getRideId())
                .build();

        try {
            paymentSagaService.startPaymentSaga(chargeRequest);
            log.info("[Consumer] Payment saga triggered successfully for rideId={}", event.getRideId());
        } catch (Exception e) {
            log.error("[Consumer] Failed to trigger payment saga for rideId={}", event.getRideId(), e);
        }
    }
}
