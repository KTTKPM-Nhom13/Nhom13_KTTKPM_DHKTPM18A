package com.cab.ride.core.service;

import com.cab.ride.core.dto.event.DriverLocationEvent;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import com.cab.ride.core.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core service của ride-service.
 *
 * <p>Chịu trách nhiệm hai logic cốt lõi:
 * <ol>
 *   <li><b>State Machine</b> — cập nhật trạng thái cuốc xe trong PostgreSQL.</li>
 *   <li><b>Real-time GPS</b> — ghi tọa độ tài xế vào Redis GEO và bắn sự kiện lên Kafka.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final String REDIS_GEO_KEY      = "driver:locations";
    private static final String KAFKA_LOCATION_TOPIC = "driver.location.updated";

    // ── Dependencies ───────────────────────────────────────────────────────
    private final RideRepository             rideRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ══════════════════════════════════════════════════════════════════════
    // LOGIC 1 — State Machine: Cập nhật trạng thái cuốc xe
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Cập nhật trạng thái của một cuốc xe.
     *
     * @param rideId    ID của cuốc xe (UUID dạng String).
     * @param newStatus Trạng thái mới cần chuyển sang.
     * @return Entity {@link Ride} sau khi đã được lưu.
     * @throws IllegalArgumentException nếu {@code rideId} không phải UUID hợp lệ.
     * @throws jakarta.persistence.EntityNotFoundException nếu không tìm thấy cuốc xe.
     */
    @Transactional
    public Ride updateRideStatus(String rideId, RideStatus newStatus) {
        UUID uuid = parseUuid(rideId);

        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> {
                    log.error("[RideService] updateRideStatus — Ride NOT FOUND: id={}", rideId);
                    return new jakarta.persistence.EntityNotFoundException(
                            "Ride not found: " + rideId);
                });

        RideStatus oldStatus = ride.getStatus();
        ride.setStatus(newStatus);
        Ride saved = rideRepository.save(ride);

        log.info("[RideService] Status transition: rideId={} | {} → {}",
                rideId, oldStatus, newStatus);

        return saved;
    }

    /**
     * Cập nhật trạng thái VÀ điền driverId cho cuốc xe (dùng cho luồng ASSIGNED).
     *
     * @param rideId    ID của cuốc xe.
     * @param driverId  ID tài xế được chỉ định.
     * @param newStatus Trạng thái mới (thường là {@link RideStatus#ASSIGNED}).
     * @return Entity {@link Ride} đã được cập nhật.
     */
    @Transactional
    public Ride assignDriverToRide(String rideId, String driverId, RideStatus newStatus) {
        UUID uuid = parseUuid(rideId);

        Ride ride = rideRepository.findById(uuid)
                .orElseThrow(() -> {
                    log.error("[RideService] assignDriverToRide — Ride NOT FOUND: id={}", rideId);
                    return new jakarta.persistence.EntityNotFoundException(
                            "Ride not found: " + rideId);
                });

        RideStatus oldStatus = ride.getStatus();

        // Guard: Chỉ chuyển trạng thái nếu cuốc xe đang ở trạng thái hợp lệ
        if (oldStatus != RideStatus.MATCHING && oldStatus != RideStatus.CREATED) {
            log.warn("[RideService] assignDriverToRide — Invalid transition: rideId={} | current={} | attempted={}",
                    rideId, oldStatus, newStatus);
            // Trả về nguyên trạng thái hiện tại, không throw để tránh crash consumer
            return ride;
        }

        ride.setDriverId(driverId);
        ride.setStatus(newStatus);
        Ride saved = rideRepository.save(ride);

        log.info("[RideService] Driver assigned: rideId={} | driverId={} | {} → {}",
                rideId, driverId, oldStatus, newStatus);

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOGIC 2 — Real-time GPS: Ghi Redis GEO + bắn Kafka event
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Xử lý tọa độ GPS thời gian thực của tài xế.
     *
     * <ol>
     *   <li>Ghi ngay vào Redis GEO key {@code driver:locations} để các service
     *       khác (matching, tracking) có thể query khoảng cách / bán kính O(1).</li>
     *   <li>Bắn sự kiện lên Kafka topic {@code driver.location.updated} bất đồng bộ.</li>
     * </ol>
     *
     * <p><b>⚡ Không đụng vào DB</b> — đảm bảo latency cực thấp cho hot-path GPS update.
     *
     * @param driverId ID của tài xế.
     * @param lat      Vĩ độ hiện tại.
     * @param lng      Kinh độ hiện tại.
     */
    public void updateDriverLocation(String driverId, double lat, double lng) {
        // ── Step 1: Ghi vào Redis GEO ────────────────────────────────────
        try {
            Long added = redisTemplate.opsForGeo()
                    .add(REDIS_GEO_KEY, new Point(lng, lat), driverId);
            // Point(longitude, latitude) — thứ tự của Spring Data Redis GEO
            log.debug("[RideService] Redis GEO updated: driverId={} | lat={} | lng={} | added={}",
                    driverId, lat, lng, added);
        } catch (Exception ex) {
            // Redis lỗi không nên làm fail toàn bộ request — log và tiếp tục
            log.error("[RideService] FAILED to write Redis GEO: driverId={} | error={}",
                    driverId, ex.getMessage(), ex);
        }

        // ── Step 2: Bắn sự kiện lên Kafka ────────────────────────────────
        DriverLocationEvent event = DriverLocationEvent.builder()
                .driverId(driverId)
                .lat(lat)
                .lng(lng)
                .timestamp(System.currentTimeMillis())
                .build();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KAFKA_LOCATION_TOPIC, driverId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[RideService] FAILED to send Kafka event: topic={} | driverId={} | error={}",
                        KAFKA_LOCATION_TOPIC, driverId, ex.getMessage(), ex);
            } else {
                log.debug("[RideService] Kafka event sent: topic={} | driverId={} | partition={} | offset={}",
                        KAFKA_LOCATION_TOPIC, driverId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private UUID parseUuid(String rideId) {
        try {
            return UUID.fromString(rideId);
        } catch (IllegalArgumentException ex) {
            log.error("[RideService] Invalid UUID format: rideId={}", rideId);
            throw new IllegalArgumentException("Invalid rideId format: " + rideId, ex);
        }
    }
}
