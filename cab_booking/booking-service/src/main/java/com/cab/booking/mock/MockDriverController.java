package com.cab.booking.mock;

import com.cab.booking.common.ApiResponse;
import com.cab.booking.core.dto.event.RideAssignedEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mock Driver Matching Service (AI Matching).
 * Giả lập endpoint AI tìm tài xế gần nhất.
 * <p>
 * Trong thực tế, Driver Service (hoặc AI Matching Service) sẽ:
 * 1. Nhận RideCreatedEvent từ Kafka (topic: ride.created)
 * 2. Gọi AI/Algorithm tìm tài xế phù hợp
 * 3. Gửi RideAssignedEvent lên Kafka (topic: ride.assigned)
 * <p>
 * Mock này:
 * - Cung cấp API /api/drivers/nearby (FE gọi để hiển thị tài xế)
 * - Cung cấp API /api/mock/driver/match (gọi thủ công để assign driver → trigger Kafka)
 */
@RestController
@RequestMapping("/api/mock/driver")
@RequiredArgsConstructor
@Slf4j
public class MockDriverController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final List<MockDriver> MOCK_DRIVERS = List.of(
            MockDriver.builder().id("drv-001").name("Lê Văn Cường").phone("0923456789")
                    .vehicleType("SEDAN").licensePlate("51A-12345").rating(4.8)
                    .lat(10.8225).lng(106.6890).build(),
            MockDriver.builder().id("drv-002").name("Phạm Thị Dung").phone("0934567890")
                    .vehicleType("SUV").licensePlate("51B-67890").rating(4.6)
                    .lat(10.8210).lng(106.6870).build(),
            MockDriver.builder().id("drv-003").name("Hoàng Văn Em").phone("0945678901")
                    .vehicleType("BIKE").licensePlate("29A1-11111").rating(4.9)
                    .lat(10.8230).lng(106.6900).build()
    );

    @GetMapping("/nearby")
    public ApiResponse<List<MockDriver>> getNearbyDrivers(
            @RequestParam("location") String location,
            @RequestParam(value = "vehicleType", required = false) String vehicleType) {

        List<MockDriver> drivers = vehicleType != null
                ? MOCK_DRIVERS.stream().filter(d -> d.getVehicleType().equals(vehicleType)).toList()
                : MOCK_DRIVERS;

        return ApiResponse.success(drivers);
    }

    /**
     * Manually trigger driver assignment → gửi ride.assigned lên Kafka.
     * Thay thế cho AI Matching Service đang không có mặt.
     *
     * @param rideId Booking ID cần assign driver
     * @return thông tin driver đã được gán
     */
    @PostMapping("/match/{rideId}")
    public ApiResponse<MockDriver> matchDriver(@PathVariable UUID rideId, @RequestParam(required = false) String vehicleType) {
        MockDriver matchedDriver = MOCK_DRIVERS.stream()
                .filter(d -> vehicleType == null || d.getVehicleType().equalsIgnoreCase(vehicleType))
                .findFirst()
                .orElse(MOCK_DRIVERS.get(0));

        RideAssignedEvent event = RideAssignedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(RideAssignedEvent.EVENT_TYPE)
                .rideId(rideId.toString())
                .driverId(matchedDriver.getId())
                .timestamp(Instant.now().toString())
                .build();

        kafkaTemplate.send("ride.assigned", event);
        log.info("🛰️ [MOCK] Đã gửi ride.assigned → Kafka | rideId={} | driver={}", rideId, matchedDriver.getId());

        return ApiResponse.success(matchedDriver);
    }

    // ========== DTO ==========
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockDriver {
        private String id;
        private String name;
        private String phone;
        private String vehicleType;
        private String licensePlate;
        private Double rating;
        private Double lat;
        private Double lng;
    }
}
