package com.cab.ride.core.listener;

import com.cab.ride.core.dto.event.inbound.PaymentCompletedEvent;
import com.cab.ride.core.dto.event.inbound.RideAssignedEvent;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer của ride-service.
 *
 * <p>Lắng nghe các topic từ các service khác trong hệ thống và điều phối
 * việc cập nhật vòng đời chuyến đi ({@link com.cab.ride.core.enums.RideStatus}).
 *
 * <ul>
 *   <li>{@code ride.assigned}     — từ matching-service → chuyển sang ASSIGNED.</li>
 *   <li>{@code payment.completed} — từ payment-service  → chuyển sang PAID.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideEventConsumer {

    private static final String GROUP_ID = "ride-service-group";

    private final RideService rideService;

    // ══════════════════════════════════════════════════════════════════════
    // Topic: ride.assigned  (matching-service → ride-service)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Nhận sự kiện khi matching-service đã chọn được tài xế phù hợp.
     * Cập nhật trạng thái cuốc xe sang {@link RideStatus#ASSIGNED} và lưu driverId vào DB.
     *
     * @param event {@link RideAssignedEvent} chứa rideId và driverId.
     */
    @KafkaListener(topics = "ride.assigned", groupId = GROUP_ID)
    public void handleRideAssigned(RideAssignedEvent event) {
        log.info("[RideEventConsumer] ← ride.assigned: rideId={} | driverId={}",
                event.getRideId(), event.getDriverId());

        if (event.getRideId() == null || event.getRideId().isBlank()) {
            log.error("[RideEventConsumer] ride.assigned — rideId is null/blank, skipping.");
            return;
        }
        if (event.getDriverId() == null || event.getDriverId().isBlank()) {
            log.error("[RideEventConsumer] ride.assigned — driverId is null/blank, skipping.");
            return;
        }

        try {
            rideService.assignDriverToRide(
                    event.getRideId(),
                    event.getDriverId(),
                    RideStatus.ASSIGNED
            );
            log.info("[RideEventConsumer] ride.assigned handled ✓ rideId={}", event.getRideId());
        } catch (Exception ex) {
            log.error("[RideEventConsumer] ride.assigned — FAILED for rideId={}: {}",
                    event.getRideId(), ex.getMessage(), ex);
            // Không re-throw để tránh Kafka retry vô tận với dữ liệu lỗi cứng.
            // Cần cấu hình Dead Letter Topic (DLT) ở production.
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Topic: payment.completed  (payment-service → ride-service)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Nhận sự kiện khi thanh toán thành công.
     * Cập nhật trạng thái cuốc xe sang {@link RideStatus#PAID}.
     *
     * @param event {@link PaymentCompletedEvent} chứa rideId và eventId.
     */
    @KafkaListener(topics = "payment.completed", groupId = GROUP_ID)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[RideEventConsumer] ← payment.completed: rideId={} | eventId={} | amount={}",
                event.getRideId(), event.getEventId(), event.getAmount());

        if (event.getRideId() == null || event.getRideId().isBlank()) {
            log.error("[RideEventConsumer] payment.completed — rideId is null/blank, skipping.");
            return;
        }

        try {
            rideService.updateRideStatus(event.getRideId(), RideStatus.PAID);
            log.info("[RideEventConsumer] payment.completed handled ✓ rideId={} → PAID",
                    event.getRideId());
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            log.error("[RideEventConsumer] payment.completed — Ride NOT FOUND: rideId={}",
                    event.getRideId());
        } catch (Exception ex) {
            log.error("[RideEventConsumer] payment.completed — FAILED for rideId={}: {}",
                    event.getRideId(), ex.getMessage(), ex);
        }
    }
}
