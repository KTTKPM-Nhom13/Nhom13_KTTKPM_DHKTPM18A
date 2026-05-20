package iuh.fit.payment_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.dto.event.RideCompletedEvent;
import iuh.fit.payment_service.service.PaymentSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

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
            log.info("[Consumer] Received ride.completed - key={}, partition={}, offset={}, eventType={}, rideId={}, bookingId={}, driverId={}, paymentMethod={}, finalFare={}",
                    key, partition, offset, event.getEventType(), event.getRideId(), event.getBookingId(),
                    event.getDriverId(), event.getPaymentMethod(), event.getFinalFare());
            paymentSagaService.settleDriverEarningFromRideCompleted(event);
            acknowledgment.acknowledge();
            log.info("[Consumer] Successfully processed ride.completed - key={}", key);
        } catch (Exception e) {
            log.error("[Consumer] Error processing ride.completed event - key={}, partition={}, offset={}",
                    key, partition, offset, e);
            acknowledgment.acknowledge();
        }
    }
}
