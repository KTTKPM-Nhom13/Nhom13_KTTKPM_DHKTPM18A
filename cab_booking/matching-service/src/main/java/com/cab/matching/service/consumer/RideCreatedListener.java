package com.cab.matching.service.consumer;

import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.core.dto.event.outbound.DriverMatchedEvent;
import com.cab.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCreatedListener {

    private final MatchingService matchingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "ride.created", groupId = "matching-group")
    public void listenRideCreated(RideCreatedEvent event) {
        log.info("⚡ [MATCHING-SERVICE] Bắt đầu xử lý cuốc xe: {}", event.bookingId());
        
        // 1. Quét Redis lấy danh sách tài xế gần nhất
        List<String> nearbyDrivers = matchingService.findNearestDrivers(event.pickupLat(), event.pickupLng());
        
        if (nearbyDrivers.isEmpty()) {
            log.error("❌ Hủy bỏ quá trình matching cho cuốc xe {} vì không có tài xế.", event.bookingId());
            return;
        }

        // 2. Chốt hạ tài xế gần nhất
        String bestDriverId = nearbyDrivers.get(0);
        log.info("🏆 Đã chọn tài xế [{}] cho cuốc xe [{}]", bestDriverId, event.bookingId());

        // 3. Bắn event báo cho Booking Service biết
        // Giả lập tọa độ của tài xế (thực tế sẽ lấy từ Redis ra)
        DriverMatchedEvent matchedEvent = DriverMatchedEvent.create(
                event.bookingId(), 
                bestDriverId, 
                event.pickupLat() + 0.001, // Giả lập tài xế cách một chút
                event.pickupLng() + 0.001
        );

        kafkaTemplate.send("driver.matched", event.bookingId(), matchedEvent);
        log.info("📤 Đã bắn sự kiện DRIVER_MATCHED lên Kafka thành công!");
    }
}
