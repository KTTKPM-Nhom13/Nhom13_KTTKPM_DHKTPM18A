package iuh.fit.driverservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.driverservice.dto.event.PaymentCompletedEvent;
import iuh.fit.driverservice.service.DriverEarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final DriverEarningService driverEarningService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.completed", groupId = "driver-service-payment-group")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            PaymentCompletedEvent event = objectMapper.convertValue(payload, PaymentCompletedEvent.class);
            log.info("[DriverEarning] Consumed payment.completed - eventId={}, rideId={}, driverId={}, amount={}",
                    event.getEventId(), event.getRideId(), event.getDriverId(), event.getAmount());
            driverEarningService.creditDriverFromPayment(event);
        } catch (Exception e) {
            log.error("[DriverEarning] Failed to process payment.completed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
