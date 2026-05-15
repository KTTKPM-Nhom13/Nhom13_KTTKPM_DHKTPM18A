package com.cab.matching.service;

import com.cab.matching.client.AiScoringClient;
import com.cab.matching.client.AiScoringResponse;
import com.cab.matching.client.DriverFeatureDto;
import com.cab.matching.client.RankingEntry;
import com.cab.matching.core.dto.event.inbound.RideCreatedEvent;
import com.cab.matching.core.dto.event.outbound.RideAssignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core logic matching tài xế cho cuốc xe.
 *
 * <p>Luồng xử lý {@link #processMatching}:
 * <ol>
 *   <li>Lấy danh sách ứng viên từ Redis GEO (kèm {@code priceMultiplier} mock).</li>
 *   <li>Gọi AI Scoring Service — Feign tự retry 3 lần khi lỗi mạng/5xx.</li>
 *   <li>Nếu AI vẫn lỗi sau 3 lần retry → Fallback: chọn tài xế gần nhất.</li>
 *   <li>Vòng lặp Race Condition (Redis SETNX): quét ranking từ top 1 → top N,
 *       xí khóa phân tán cho tài xế, gán cuốc xe ngay khi thành công.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final String DRIVER_LOCATION_KEY  = "driver:locations";
    private static final String DRIVER_STATUS_PREFIX = "driver:status:";
    private static final String DRIVER_LOCK_PREFIX   = "driver:lock:";
    private static final String DRIVER_STATUS_AVAILABLE = "AVAILABLE";
    private static final String DRIVER_LOCK_VALUE    = "LOCKED";
    private static final String BOOKING_CANCELLED_PREFIX = "booking:cancelled:";
    private static final long   LOCK_TTL_SECONDS     = 60L;
    private static final int    MAX_MATCHING_ATTEMPTS = 3;
    private static final String TOPIC_RIDE_ASSIGNED  = "ride.assigned";

    // ── Dependencies ───────────────────────────────────────────────────────
    private final RedisTemplate<String, String>  redisTemplate;
    private final AiScoringClient                aiScoringClient;
    private final KafkaTemplate<String, Object>  kafkaTemplate;

    private final Random random = new Random();

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Entrypoint xử lý toàn bộ vòng đời matching một cuốc xe.
     *
     * @param event sự kiện RideCreated từ booking-service.
     */
    public void processMatching(RideCreatedEvent event) {
        if (isBookingCancelled(event.rideId())) {
            log.info("Skip matching cancelled rideId={}", event.rideId());
            return;
        }
        log.info("⚡ [STEP 1] Bat dau matching | rideId={}", event.rideId());

        // ── STEP 1: Lấy ứng viên từ Redis GEO (có priceMultiplier) ────────
        List<DriverFeatureDto> candidates = fetchCandidatesFromRedis(
                event.pickupLat(), event.pickupLng(), event.searchRadiusKmOrDefault());

        if (candidates.isEmpty()) {
            log.warn("⚠️ Khong tim thay tai xe nao xung quanh rideId={}. Bo qua.", event.rideId());
            // TODO: Publish NO_DRIVER_AVAILABLE event hoặc đẩy vào DLQ
            scheduleRetryIfPossible(event);
            return;
        }

        // ── STEP 2: Gọi AI Scoring — Feign tự retry (FeignConfig.aiScoringRetryer) ──
        List<RankingEntry> ranking = callAiWithFallback(candidates, event.rideId());

        if (ranking.isEmpty()) {
            log.error("❌ Ranking rong sau AI + Fallback cho rideId={}. Abort.", event.rideId());
            return;
        }

        // ── STEP 3: Race Condition — SETNX vòng lặp từ top 1 đến top N ───
        assignDriverWithLock(ranking, event);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE — AI CALL + FALLBACK
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Gọi AI Scoring Service. Feign tự động retry 3 lần (cấu hình trong {@code FeignConfig}).
     * Nếu vẫn lỗi sau retry → fallback: tạo ranking theo thứ tự gần nhất (candidates đã sort asc).
     *
     * @param candidates danh sách ứng viên đã có priceMultiplier.
         * @param rideId  ID cuoc xe (dung cho logging).
     * @return ranking đã sắp xếp từ cao xuống thấp.
     */
        private List<RankingEntry> callAiWithFallback(List<DriverFeatureDto> candidates, String rideId) {
        try {
            log.info("🤖 [STEP 2] Goi AI Scoring cho {} ung vien | rideId={}", candidates.size(), rideId);
            AiScoringResponse response = aiScoringClient.getBestMatch(candidates);
            log.info("🏆 AI de xuat: bestDriver={} | score={} | rideId={}",
                response.getBestDriverId(), response.getHighestScore(), rideId);
            return response.getRanking() != null ? response.getRanking() : List.of();

        } catch (Exception ex) {
            // Feign đã retry 3 lần và thất bại — chuyển sang fallback
            log.error("🔥 AI Service that bai sau 3 lan retry: {}. Fallback → tai xe gan nhat | rideId={}",
                ex.getMessage(), rideId);

            // Fallback: tạo ranking thủ công từ candidates (đã sắp xếp distance asc)
            List<RankingEntry> fallbackRanking = new ArrayList<>();
            for (DriverFeatureDto c : candidates) {
                fallbackRanking.add(RankingEntry.builder()
                        .driverId(c.getDriverId())
                        .score(0.0)
                        .details("fallback-nearest-driver")
                        .build());
            }
            return fallbackRanking;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE — RACE CONDITION: Redis SETNX Distributed Lock Loop
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Vòng lặp xử lý Race Condition: quét ranking từ điểm cao xuống thấp,
     * dùng Redis {@code SETNX} (setIfAbsent) để tranh giành quyền gán tài xế.
     *
     * <p><b>Tại sao cần SETNX?</b><br>
     * Trong môi trường nhiều instance matching-service chạy song song,
     * hai cuốc xe có thể đọc cùng một tài xế từ Redis GEO và cùng
     * cố gán tài xế đó. {@code setIfAbsent} là lệnh atomic trên Redis,
     * đảm bảo chỉ một request thành công (trả về {@code true}),
     * request còn lại thất bại ({@code false}) → thử tài xế tiếp theo.
     *
     * @param ranking danh sách xếp hạng từ AI (đã sắp xếp điểm cao → thấp).
         * @param event   su kien cuoc xe goc (chua rideId, toa do...).
     */
    private void assignDriverWithLock(List<RankingEntry> ranking, RideCreatedEvent event) {
        log.info("🔐 [STEP 3] Race Condition Lock Loop — {} ung vien | rideId={}",
            ranking.size(), event.rideId());

        for (RankingEntry entry : ranking) {
            String driverId  = normalizeDriverId(entry.getDriverId());
            String lockKey   = DRIVER_LOCK_PREFIX + driverId;

            // ── SETNX Atomic: Thử đặt khóa phân tán cho tài xế này ───────
            // Lệnh = SET lockKey "LOCKED" NX EX 5
            //   NX  → Chỉ set nếu key CHƯA tồn tại (đảm bảo tính nguyên tử)
            //   EX 5→ TTL 5 giây: tự giải phóng nếu service crash giữa chừng
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, DRIVER_LOCK_VALUE, LOCK_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(locked)) {
                // ✅ XÍ ĐƯỢC KHÓA — tài xế này chưa bị cuốc nào khác lấy
                // Critical section: thực hiện gán tài xế và thay đổi trạng thái
                log.info("✅ Da xi driver={} cho rideId={} | score={}",
                    driverId, event.rideId(), entry.getScore());

                // Đổi trạng thái tài xế → BUSY trong Redis (TTL 30 phút)
                // Tài xế BUSY sẽ không xuất hiện trong kết quả GEO search tiếp theo
                // Bắn sự kiện ride.assigned lên Kafka
                // → ride-service sẽ lắng nghe và cập nhật DB (MATCHING → ASSIGNED)
                RideAssignedEvent assignedEvent = RideAssignedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RIDE_ASSIGNED")
                    .rideId(event.rideId())
                    .bookingId(event.rideId())
                    .driverId(driverId)
                    .timestamp(Instant.now().toString())
                    .build();

                log.info("Publishing ride.assigned | topic={} | rideId={} | payload={}",
                    TOPIC_RIDE_ASSIGNED, event.rideId(), assignedEvent);
                kafkaTemplate.send(TOPIC_RIDE_ASSIGNED, event.rideId(), assignedEvent);

                log.info("Da ban ride.assigned | rideId={} | driverId={}", event.rideId(), driverId);
                return;

            } else {
                // ❌ THUA KHÓA — tài xế này vừa bị một cuốc xe song song giành mất
                // Log cảnh báo và tiếp tục sang tài xế xếp hạng kế tiếp (top 2, top 3...)
                log.warn("⚡ Race Condition! driver={} da bi cuoc khac xi. Thu tai xe tiep theo | rideId={}",
                    driverId, event.rideId());
                // → for-loop tiếp tục iteration tiếp theo
            }
        }

        // Đến đây mà không break = toàn bộ tài xế trong ranking đều bị xí mất
        log.warn("⚠️ Khong con tai xe kha dung sau Race Condition loop | rideId={}", event.rideId());
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE — REDIS GEO FETCH
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Lấy danh sách tối đa 10 tài xế trong bán kính 3km từ Redis GEO.
     * Mỗi tài xế được gán {@code priceMultiplier} ngẫu nhiên 1.0–1.5
     * (mock surge pricing; production nên lấy từ pricing-service).
     *
     * @param lat vĩ độ điểm đón.
     * @param lng kinh độ điểm đón.
     * @return danh sách FeatureDto sắp xếp gần nhất trước.
     */
    private List<DriverFeatureDto> fetchCandidatesFromRedis(Double lat, Double lng, double radiusKm) {
        GeoReference<String> reference = GeoReference.fromCoordinate(lng, lat);
        Distance             radius    = new Distance(radiusKm, Metrics.KILOMETERS);

        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(10);

        List<DriverFeatureDto> featureList = new ArrayList<>();

        try {
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    redisTemplate.opsForGeo().search(DRIVER_LOCATION_KEY, reference, radius, args);

            if (results == null || results.getContent().isEmpty()) {
                return Collections.emptyList();
            }

            // Lấy trước toàn bộ trạng thái của các tài xế để tối ưu performance (tránh gọi Redis n lần)
            List<String> statusKeys = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> res : results.getContent()) {
                statusKeys.add(DRIVER_STATUS_PREFIX + normalizeDriverId(res.getContent().getName()));
            }
            List<String> statuses = redisTemplate.opsForValue().multiGet(statusKeys);

            for (int i = 0; i < results.getContent().size(); i++) {
                GeoResult<RedisGeoCommands.GeoLocation<String>> res = results.getContent().get(i);
                String rawDriverId     = res.getContent().getName();
                String driverId        = normalizeDriverId(rawDriverId);

                // Kiểm tra trạng thái tài xế, nếu BUSY thì bỏ qua không cho vào danh sách ứng viên
                String status = (statuses != null && i < statuses.size()) ? statuses.get(i) : null;
                if (!DRIVER_STATUS_AVAILABLE.equals(status)) {
                    log.debug("Driver {} status is {}, skipping.", driverId, status);
                    continue;
                }

                double distKm          = res.getDistance().getValue();
                int    etaMin          = Math.max(2, (int) (distKm * 3));     // 3 phút/km, min 2p
                double rating          = 4.5 + random.nextDouble() * 0.5;     // mock 4.5 – 5.0

                // FIX #1 — priceMultiplier: mock surge pricing 1.0 – 1.5
                // TODO production: gọi pricing-service.getSurgeMultiplier(driverId, zone)
                double priceMultiplier = 1.0 + random.nextDouble() * 0.5;

                featureList.add(DriverFeatureDto.builder()
                        .driverId(driverId)
                        .distance(distKm)
                        .eta(etaMin)
                        .rating(rating)
                        .priceMultiplier(priceMultiplier) // ← FIX #1
                        .build());
            }

            log.info("📍 Tìm được {} ứng viên từ Redis GEO (3km radius) sau khi filter AVAILABLE", featureList.size());

        } catch (Exception ex) {
            log.error("❌ Lỗi khi đọc Redis GEO: {}", ex.getMessage(), ex);
        }

        return featureList;
    }

    private void scheduleRetryIfPossible(RideCreatedEvent event) {
        int currentAttempt = event.attemptOrDefault();
        if (currentAttempt >= MAX_MATCHING_ATTEMPTS || isBookingCancelled(event.rideId())) {
            log.warn("No retry for rideId={} | attempt={} | cancelled={}",
                    event.rideId(),
                    currentAttempt,
                    isBookingCancelled(event.rideId()));
            return;
        }

        int nextAttempt = currentAttempt + 1;
        double nextRadiusKm = switch (nextAttempt) {
            case 2 -> 5.0;
            case 3 -> 8.0;
            default -> event.searchRadiusKmOrDefault();
        };

        RideCreatedEvent retryEvent = new RideCreatedEvent(
                UUID.randomUUID().toString(),
                event.type(),
                event.rideId(),
                event.customerId(),
                event.customerNote(),
                event.pickup(),
                event.dropoff(),
                event.vehicleType(),
                event.paymentMethod(),
                event.estimatedFare(),
                event.promoCode(),
                nextAttempt,
                nextRadiusKm,
                event.isRematch(),
                event.excludedDriverIds(),
                Instant.now().toString()
        );

        java.util.concurrent.CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            if (isBookingCancelled(event.rideId())) {
                log.info("Skip scheduled retry for cancelled rideId={}", event.rideId());
                return;
            }
            kafkaTemplate.send("ride.created", retryEvent.rideId(), retryEvent);
            log.info("Scheduled matching retry published | rideId={} | attempt={} | radiusKm={}",
                    retryEvent.rideId(),
                    retryEvent.matchingAttempt(),
                    retryEvent.searchRadiusKm());
        });
    }

    private boolean isBookingCancelled(String rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BOOKING_CANCELLED_PREFIX + rideId));
    }

    private String normalizeDriverId(String driverId) {
        if (driverId == null) {
            return null;
        }
        return driverId.replace("\"", "").trim();
    }
}
