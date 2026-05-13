package iuh.fit.payment_service.consumer;

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

    @KafkaListener(
            topics = "booking.failed",
            groupId = "payment-booking-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBookingFailedEvent(
            @Payload BookingFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        log.info("[Compensate] Received booking.failed - key={}, partition={}, offset={}, bookingId={}, reason={}",
                key, partition, offset, event.getBookingId(), event.getReason());

        try {
            String bookingId = event.getBookingId();
            if (bookingId == null || bookingId.isBlank()) {
                log.warn("[Compensate] booking.failed event has null/blank bookingId, skipping");
                acknowledgment.acknowledge();
                return;
            }

            compensationService.compensatePaymentByBookingId(bookingId, event.getReason());
            acknowledgment.acknowledge();
            log.info("[Compensate] Compensation triggered for bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("[Compensate] Error processing booking.failed - bookingId={}: {}",
                    event.getBookingId(), e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}
