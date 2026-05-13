package com.cab.matching.service.consumer;

import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCreatedListener {

    private final MatchingService matchingService;

    /**
     * Lắng nghe sự kiện có cuốc xe mới được tạo trên hệ thống.
     * Topic: ride.created
     */
    @KafkaListener(topics = "ride.created", groupId = "matching-group")
    public void listenRideCreated(RideCreatedEvent event) {
        log.info("📥 Nhận sự kiện RideCreated: [BookingId={}, CustomerId={}]", 
                event.bookingId(), event.customerId());
        
        try {
            // Ủy nhiệm việc xử lý cho Brain (MatchingService)
            matchingService.processMatching(event);
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR khi xử lý event matching cho bookingId={}: {}", 
                    event.bookingId(), e.getMessage(), e);
        }
    }
}
