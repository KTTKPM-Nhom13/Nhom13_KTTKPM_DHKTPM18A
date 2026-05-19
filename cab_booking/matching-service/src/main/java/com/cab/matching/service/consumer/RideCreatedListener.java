package com.cab.matching.service.consumer;

import com.cab.matching.core.dto.event.inbound.DriverRejectedEvent;
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
     * Topic: booking.created (canonical), ride.created (legacy compatibility)
     */
    @KafkaListener(topics = {"booking.created", "ride.created"}, groupId = "matching-group")
    public void listenRideCreated(RideCreatedEvent event) {
        log.info("📥 Nhan su kien RideCreated: [RideId={}, CustomerId={}]", 
            event.rideId(), event.customerId());
        
        try {
            // Ủy nhiệm việc xử lý cho Brain (MatchingService)
            matchingService.processMatching(event);
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR khi xu ly event matching cho rideId={}: {}", 
                    event.rideId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "driver.rejected", groupId = "matching-group")
    public void listenDriverRejected(DriverRejectedEvent event) {
        log.info("Received driver.rejected: rideId={} | driverId={}", event.aggregateId(), event.getDriverId());
        try {
            matchingService.processDriverRejected(event);
        } catch (Exception e) {
            log.error("Failed to rematch rideId={} after driver rejection: {}",
                    event.aggregateId(), e.getMessage(), e);
        }
    }
}
