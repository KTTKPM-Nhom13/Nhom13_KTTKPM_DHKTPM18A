package iuh.fit.payment_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.dto.event.RideCompletedEvent;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideCompletedConsumer {

    private final PaymentSagaService paymentSagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "ride.completed",
            groupId = "payment-ride-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRideCompletedEvent(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        try {
            RideCompletedEvent event = objectMapper.convertValue(payload, RideCompletedEvent.class);
            log.info("[Consumer] Received ride.completed - key={}, partition={}, offset={}, eventType={}, rideId={}, finalFare={}",
                    key, partition, offset, event.getEventType(), event.getRideId(), event.getFinalFare());
            processPaymentFromRideCompleted(event);
            acknowledgment.acknowledge();
            log.info("[Consumer] Successfully processed ride.completed - key={}", key);
        } catch (Exception e) {
            log.error("[Consumer] Error processing ride.completed event - key={}, partition={}, offset={}",
                    key, partition, offset, e);
            acknowledgment.acknowledge();
        }
    }

    private void processPaymentFromRideCompleted(RideCompletedEvent event) {
        String rideId = event.getRideId();
        log.info("[Consumer] Initiating automatic payment for rideId={}", rideId);

        PaymentMethod method = PaymentMethod.CASH;
        if (event.getPaymentMethod() != null) {
            try {
                method = PaymentMethod.valueOf(event.getPaymentMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("[Consumer] Unknown payment method '{}', defaulting to CASH", event.getPaymentMethod());
            }
        }

        if (method != PaymentMethod.CASH) {
            log.info("[Consumer] Skipping ride.completed payment for prepaid method={} rideId={}. Online payments are initiated earlier.",
                    method, rideId);
            return;
        }

        if (event.getDriverId() == null || event.getDriverId().isBlank()) {
            log.warn("[Consumer] Skipping CASH settlement - missing driverId for rideId={}", rideId);
            return;
        }

        BigDecimal amount = event.getFinalFare();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[Consumer] Skipping payment - invalid finalFare {} for rideId={}", amount, rideId);
            return;
        }

        ChargePaymentRequest chargeRequest = ChargePaymentRequest.builder()
                .bookingId(rideId)
                .customerId(event.getCustomerId())
                .driverId(event.getDriverId())
                .amount(amount)
                .currency("VND")
                .paymentMethod(method)
                .description("Auto-payment for ride " + rideId)
                .idempotencyKey("ride-completed-" + rideId)
                .build();

        try {
            paymentSagaService.startPaymentSaga(chargeRequest);
            log.info("[Consumer] Payment saga triggered successfully for rideId={}", rideId);
        } catch (Exception e) {
            log.error("[Consumer] Failed to trigger payment saga for rideId={}", rideId, e);
        }
    }
}
