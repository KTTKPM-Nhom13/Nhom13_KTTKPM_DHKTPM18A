package iuh.fit.driverservice.consumer;

import iuh.fit.driverservice.dto.event.PaymentCompletedEvent;
import iuh.fit.driverservice.service.DriverEarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment.completed events with typed DTO.
 * Deserialization handled by JsonDeserializer via type mapping in application.yaml.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final DriverEarningService driverEarningService;

    @KafkaListener(topics = "payment.completed", groupId = "driver-service-payment-group")
    public void consumePaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("[DriverEarning] Consumed payment.completed - eventId={}, rideId={}, driverId={}, amount={}",
                    event.getEventId(), event.getRideId(), event.getDriverId(), event.getAmount());
            driverEarningService.creditDriverFromPayment(event);
        } catch (Exception e) {
            log.error("[DriverEarning] Failed to process payment.completed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
