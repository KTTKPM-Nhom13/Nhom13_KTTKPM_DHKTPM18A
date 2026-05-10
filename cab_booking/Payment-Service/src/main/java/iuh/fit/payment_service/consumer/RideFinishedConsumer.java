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
import java.time.Instant;
import java.util.Map;

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
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("[Consumer] Received ride.finished event - key={}, partition={}, offset={}", key, partition, offset);

        try {
            RideFinishedEvent rideEvent = mapToRideFinishedEvent(event);
            log.info("[Consumer] Parsed ride.finished - eventId={}, rideId={}, amount={}, customerId={}",
                    rideEvent.getEventId(), rideEvent.getRideId(), rideEvent.getFinalAmount(), rideEvent.getCustomerId());

            processPaymentFromRideFinished(rideEvent);
            acknowledgment.acknowledge();
            log.info("[Consumer] Successfully processed ride.finished - key={}", key);
        } catch (Exception e) {
            log.error("[Consumer] Error processing ride.finished event - key={}", key, e);
            acknowledgment.acknowledge();
        }
    }

    private RideFinishedEvent mapToRideFinishedEvent(Map<String, Object> raw) {
        return RideFinishedEvent.builder()
                .eventId(getString(raw, "eventId"))
                .type(getString(raw, "type"))
                .rideId(getString(raw, "rideId"))
                .customerId(getString(raw, "customerId"))
                .driverId(getString(raw, "driverId"))
                .totalAmount(getBigDecimal(raw, "totalAmount"))
                .baseFare(getBigDecimal(raw, "baseFare"))
                .distanceKm(getBigDecimal(raw, "distanceKm"))
                .durationMinutes(getBigDecimal(raw, "durationMinutes"))
                .surgeMultiplier(getBigDecimal(raw, "surgeMultiplier"))
                .discountAmount(getBigDecimal(raw, "discountAmount"))
                .finalAmount(getBigDecimal(raw, "finalAmount"))
                .currency(getString(raw, "currency"))
                .paymentMethod(getString(raw, "paymentMethod"))
                .bookingId(getString(raw, "bookingId"))
                .completedAt(getInstant(raw, "completedAt"))
                .schemaVersion(getString(raw, "schemaVersion"))
                .build();
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

        BigDecimal amount = event.getFinalAmount() != null ? event.getFinalAmount() : event.getTotalAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[Consumer] Skipping payment - invalid amount {} for rideId={}", amount, event.getRideId());
            return;
        }

        ChargePaymentRequest chargeRequest = ChargePaymentRequest.builder()
                .rideId(event.getRideId())
                .customerId(event.getCustomerId())
                .amount(amount)
                .currency(event.getCurrency() != null ? event.getCurrency() : "VND")
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

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof String) {
            try { return new BigDecimal((String) value); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Instant getInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof String) {
            try { return Instant.parse((String) value); } catch (Exception e) { return null; }
        }
        if (value instanceof Number) return Instant.ofEpochMilli(((Number) value).longValue());
        return null;
    }
}
