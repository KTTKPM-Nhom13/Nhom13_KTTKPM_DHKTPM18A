package com.cab.ride.core.controller;

import com.cab.ride.core.dto.request.LocationUpdateRequest;
import com.cab.ride.core.service.RideLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST fallback endpoint for driver GPS location updates.
 *
 * <p>Secured with JWT — driverId is extracted from the token, NOT from the request body.
 * Prefer Socket.IO ({@code ws://host:9095}) for realtime; this endpoint exists for
 * backward compatibility and Postman testing.
 *
 * <p><b>Production requirement:</b> {@code rideId} is mandatory. Requests without rideId
 * return {@code 400 BAD_REQUEST}. The legacy mode (no rideId) is deprecated and only
 * available for debug purposes.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Location API", description = "Nhận tọa độ GPS thời gian thực từ tài xế")
public class LocationController {

    private final RideLocationService rideLocationService;

    @Operation(
            summary = "Cập nhật vị trí tài xế (GPS)",
            description = "Nhận tọa độ từ Driver App (JWT auth), ghi Redis GEO, bắn Kafka event. "
                    + "Production callers MUST include rideId. Omitting rideId returns 400 BAD_REQUEST.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nhận tọa độ thành công"),
                    @ApiResponse(responseCode = "400", description = "rideId missing or dữ liệu đầu vào không hợp lệ"),
                    @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
                    @ApiResponse(responseCode = "403", description = "Không có quyền")
            }
    )
    @PostMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LocationUpdateRequest request) {

        String driverId = currentDriverId(jwt);
        log.debug("[LocationController] POST /location: driverId={} | rideId={} | lat={} | lng={}",
                driverId, request.getRideId(), request.getLat(), request.getLng());

        if (request.getRideId() == null || request.getRideId().isBlank()) {
            // Legacy mode deprecated — rideId is now required for production.
            // Keeping the endpoint alive but rejecting missing rideId.
            log.warn("[LocationController] DEPRECATED: location update without rideId rejected. driverId={}", driverId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rideId is required. Legacy mode without rideId is deprecated.");
        }

        // Full validation path: ride-aware location update
        rideLocationService.updateLocation(
                driverId, request.getRideId(),
                request.getLat(), request.getLng(),
                request.getHeading(), request.getSpeed());

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

    private String currentDriverId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Driver identity is required");
        }
        return jwt.getSubject();
    }
}
