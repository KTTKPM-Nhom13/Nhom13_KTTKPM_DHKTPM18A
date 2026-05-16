package iuh.fit.payment_service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.dto.event.BookingFailedEvent;
import iuh.fit.payment_service.service.PaymentCompensationService;
import iuh.fit.payment_service.service.PaymentSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingFailedConsumer {

    private final PaymentSagaService paymentSagaService;
    private final PaymentCompensationService compensationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "booking.failed",
            groupId = "payment-booking-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBookingFailedEvent(
            @Payload Object payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        try {
            BookingFailedEvent event = objectMapper.convertValue(payload, BookingFailedEvent.class);
            log.info("[Compensate] Received booking.failed - key={}, partition={}, offset={}, rideId={}, reason={}",
                key, partition, offset, event.getRideId(), event.getReason());

            String bookingId = event.getRideId();
            if (bookingId == null || bookingId.isBlank()) {
                log.warn("[Compensate] booking.failed event has null/blank rideId, skipping");
                acknowledgment.acknowledge();
                return;
            }

            compensationService.compensatePaymentByBookingId(bookingId, event.getReason());
            acknowledgment.acknowledge();
            log.info("[Compensate] Compensation triggered for bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("[Compensate] Error processing booking.failed - key={}, partition={}, offset={}: {}",
                    key, partition, offset, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}
