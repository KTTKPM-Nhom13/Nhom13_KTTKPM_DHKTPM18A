package com.cab.ride.websocket;

import com.cab.ride.core.service.RideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP Controller nhận tọa độ GPS thời gian thực từ Driver App qua WebSocket.
 *
 * <p><b>Destination:</b> {@code /app/location.update}<br>
 * Client (Driver App) gửi frame STOMP đến destination này với payload JSON:
 * <pre>
 * {
 *   "driverId": "drv-001",
 *   "lat": 10.762622,
 *   "lng": 106.660172
 * }
 * </pre>
 *
 * <p><b>Zero-DB hot-path:</b> Handler chỉ delegate xuống {@link RideService#updateDriverLocation}
 * — ghi Redis GEO + bắn Kafka async. Không bao giờ truy vấn PostgreSQL.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final RideService rideService;

    /**
     * Xử lý tọa độ GPS nhận qua STOMP WebSocket.
     *
     * <p>Flow:
     * <ol>
     *   <li>Spring tự động deserialize JSON frame → {@link LocationMessage}.</li>
     *   <li>Delegate sang {@link RideService#updateDriverLocation} (Redis GEO + Kafka).</li>
     *   <li>Không block — Kafka send là bất đồng bộ.</li>
     * </ol>
     *
     * @param message payload GPS từ tài xế.
     */
    @MessageMapping("/location.update")
    public void handleLocationUpdate(LocationMessage message) {
        log.debug("[WS] ← /app/location.update: driverId={} | lat={} | lng={}",
                message.getDriverId(), message.getLat(), message.getLng());

        if (message.getDriverId() == null || message.getDriverId().isBlank()) {
            log.warn("[WS] location.update — driverId is null/blank, ignoring frame.");
            return;
        }

        try {
            rideService.updateDriverLocation(
                    message.getDriverId(),
                    message.getLat(),
                    message.getLng()
            );
        } catch (Exception ex) {
            // Không throw ra ngoài — tránh đóng WebSocket session của tài xế
            log.error("[WS] location.update — FAILED for driverId={}: {}",
                    message.getDriverId(), ex.getMessage(), ex);
        }
    }
}
