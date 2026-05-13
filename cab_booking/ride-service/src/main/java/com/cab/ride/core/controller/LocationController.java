package com.cab.ride.core.controller;

import com.cab.ride.core.dto.request.LocationUpdateRequest;
import com.cab.ride.core.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller nhận cập nhật tọa độ GPS thời gian thực từ ứng dụng tài xế.
 *
 * <p><b>Design principle — Zero-DB hot-path:</b><br>
 * API này chỉ ghi vào Redis GEO và bắn Kafka event.
 * Không thực hiện bất kỳ thao tác nào với PostgreSQL → latency cực thấp.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Location API", description = "Nhận tọa độ GPS thời gian thực từ tài xế")
public class LocationController {

    private final RideService rideService;

    /**
     * Cập nhật tọa độ GPS của tài xế.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate request (Bean Validation).</li>
     *   <li>Gọi {@link RideService#updateDriverLocation} → ghi Redis GEO + bắn Kafka.</li>
     *   <li>Trả về 200 OK ngay lập tức — không chờ Kafka confirm.</li>
     * </ol>
     *
     * @param request DTO chứa {@code driverId}, {@code lat}, {@code lng}.
     * @return HTTP 200 OK
     */
    @Operation(
            summary = "Cập nhật vị trí tài xế (GPS)",
            description = "Nhận tọa độ từ Driver App, ghi Redis GEO, bắn Kafka event. Không đụng DB.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nhận tọa độ thành công"),
                    @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ")
            }
    )
    @PostMapping("/location")
    public ResponseEntity<Void> updateLocation(@Valid @RequestBody LocationUpdateRequest request) {
        log.debug("[LocationController] POST /location: driverId={} | lat={} | lng={}",
                request.getDriverId(), request.getLat(), request.getLng());

        rideService.updateDriverLocation(
                request.getDriverId(),
                request.getLat(),
                request.getLng()
        );

        // Trả về 200 OK cực nhanh — không đợi Kafka ack, không query DB
        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint (public, không cần JWT).
     */
    @Operation(summary = "Ping", description = "Health check đơn giản")
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("ride-service is running ✓");
    }
}
