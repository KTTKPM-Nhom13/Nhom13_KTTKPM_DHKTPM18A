package iuh.fit.payment_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.dto.event.BookingCreatedEvent;
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
public class BookingCreatedConsumer {

    private final PaymentSagaService paymentSagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "ride.created",
            groupId = "payment-booking-created-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBookingCreated(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment
    ) {
        try {
            BookingCreatedEvent event = objectMapper.convertValue(payload, BookingCreatedEvent.class);
            log.info("[Consumer] Received ride.created - key={}, partition={}, offset={}, bookingId={}, paymentMethod={}, amount={}",
                    key, partition, offset, event.getBookingId(), event.getPaymentMethod(), event.getAmount());
            paymentSagaService.initiatePaymentFromBookingCreated(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[Consumer] Error processing ride.created - key={}, partition={}, offset={}",
                    key, partition, offset, e);
            acknowledgment.acknowledge();
        }
    }
}
