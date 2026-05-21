package iuh.fit.pricing_service.controller;

import iuh.fit.pricing_service.config.PricingConfigProperties;
import iuh.fit.pricing_service.model.DemandSupplyRequest;
import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.model.FareEstimateRequest;
import iuh.fit.pricing_service.model.FareEstimateResponse;
import iuh.fit.pricing_service.model.PricingTestRequest;
import iuh.fit.pricing_service.model.PricingTestResponse;
import iuh.fit.pricing_service.model.SurgeUpdateRequest;
import iuh.fit.pricing_service.service.PricingService;
import iuh.fit.pricing_service.service.SurgePricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Pricing API", description = "APIs for fare estimation and surge pricing management")
public class PricingController {

    private final PricingService pricingService;
    private final SurgePricingService surgePricingService;
    private final PricingConfigProperties pricingConfig;

    @PostMapping("/calculate")
    @Operation(
            summary = "Calculate simple price",
            description = "Calculate price based on distance and demand index for testing"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Price calculated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<PricingTestResponse> calculateSimplePrice(
            @RequestBody @Valid PricingTestRequest request) {

        log.info("Received simple pricing request - distance: {} km, demandIndex: {}",
                request.getDistanceKm(), request.getDemandIndex());

        PricingTestResponse response = pricingService.calculateSimplePrice(
                request.getDistanceKm(), request.getDemandIndex());

        log.info("Simple price calculated - total: {}", response.getTotalFare());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-mapbox")
    @Operation(
            summary = "Test Mapbox Connection",
            description = "Test direct connection to Mapbox API to debug keys and limits"
    )
    public ResponseEntity<Map<String, Object>> testMapbox() {
        log.info("Received test Mapbox request");
        Map<String, Object> result = pricingService.testMapboxConnection();
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }



    @PostMapping("/estimate")
    @Operation(
            summary = "Get fare estimate",
            description = "Calculate and return fare estimate based on pickup/dropoff locations and vehicle type"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare estimate calculated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FareEstimateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<FareEstimateResponse> getEstimate(
            @RequestBody @Valid FareEstimateRequest request,

            @Parameter(description = "Estimated duration in minutes (optional)")
            @RequestParam(required = false) Integer estimatedDurationMinutes,

            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("Received fare estimate request - pickup: ({}, {}), dropoff: ({}, {}), vehicle: {}, duration: {}",
                request.getPickupLat(), request.getPickupLng(),
                request.getDropoffLat(), request.getDropoffLng(),
                request.getVehicleType(), estimatedDurationMinutes);

        FareEstimateResponse response = pricingService.calculateFareEstimate(request, idempotencyKey);

        log.info("Fare estimate generated - estimateId: {}, totalFare: {} {}",
                response.getEstimateId(), response.getTotalFare(), response.getCurrency());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm/{estimateId}")
    @Operation(
            summary = "Confirm fare estimate",
            description = "Confirm a previously generated fare estimate to lock in the price"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fare confirmed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FareEstimate.class))),
            @ApiResponse(responseCode = "400", description = "Invalid estimate or estimate expired"),
            @ApiResponse(responseCode = "404", description = "Estimate not found")
    })
    public ResponseEntity<FareEstimate> confirmEstimate(
            @Parameter(description = "Estimate ID to confirm", required = true)
            @PathVariable String estimateId,
            @RequestHeader(value = "X-Quote-Hash", required = false) String quoteHash) {

        log.info("Received confirm estimate request - estimateId: {}", estimateId);
        FareEstimate confirmed = pricingService.confirmFare(estimateId, quoteHash);
        log.info("Fare confirmed - estimateId: {}, totalFare: {} {}", estimateId, confirmed.getTotalFare(), confirmed.getCurrency());
        return ResponseEntity.ok(confirmed);
    }

    @GetMapping("/surge/{zoneId}")
    @Operation(
            summary = "Get surge multiplier for zone",
            description = "Retrieve the current surge multiplier for a specific zone"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Surge multiplier retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Zone not found")
    })
    public ResponseEntity<Map<String, Object>> getSurgeMultiplier(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId) {

        log.info("Received surge multiplier request - zoneId: {}", zoneId);
        BigDecimal multiplier = surgePricingService.getSurgeMultiplier(zoneId);

        Map<String, Object> response = Map.of(
                "zone_id", zoneId,
                "surge_multiplier", multiplier,
                "message", "Surge multiplier retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/surge/{zoneId}", method = {RequestMethod.PUT})
    @Operation(
            summary = "Update surge multiplier for zone",
            description = "Manually update the surge multiplier for a specific zone"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Surge multiplier updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid surge multiplier value")
    })
    public ResponseEntity<Map<String, Object>> updateSurgeMultiplier(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId,

            @RequestBody @Valid SurgeUpdateRequest request) {

        log.info("Received surge update request - zoneId: {}, multiplier: {}", zoneId, request.getMultiplier());
        pricingService.updateSurgeForZone(zoneId, request.getMultiplier());

        Map<String, Object> response = Map.of(
                "zone_id", zoneId,
                "surge_multiplier", request.getMultiplier(),
                "message", "Surge multiplier updated successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/config")
    @Operation(
            summary = "Get pricing configuration",
            description = "Retrieve current rule-based pricing configuration"
    )
    public ResponseEntity<Map<String, Object>> getPricingConfig() {
        Map<String, Object> response = Map.of(
                "calculation", pricingConfig.getCalculation(),
                "vehicle", pricingConfig.getVehicle(),
                "surge", pricingConfig.getSurge(),
                "weather", pricingConfig.getWeather(),
                "cache", pricingConfig.getCache(),
                "eta", pricingConfig.getEta()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/surge/all")
    @Operation(
            summary = "Get all surge multipliers",
            description = "Retrieve all current surge multipliers for all zones"
    )
    public ResponseEntity<Map<String, BigDecimal>> getAllSurgeMultipliers() {
        log.info("Received request to get all surge multipliers");
        Map<String, BigDecimal> allSurge = surgePricingService.getAllSurgeMultipliers();
        return ResponseEntity.ok(allSurge);
    }

    @PostMapping("/demand-supply")
    @Operation(
            summary = "Update demand and supply metrics",
            description = "Cache demand and supply metrics in Redis. Surge pricing is recalculated asynchronously by scheduler."
    )
    public ResponseEntity<Map<String, Object>> updateDemandSupply(
            @RequestBody @Valid DemandSupplyRequest request) {

        log.info("Received demand/supply update - zoneId: {}, drivers: {}, rides: {}",
                request.getZoneId(), request.getActiveDrivers(), request.getPendingRides());
        pricingService.processDemandSupplyUpdate(request.getZoneId(), request.getActiveDrivers(), request.getPendingRides());

        Map<String, Object> response = Map.of(
                "zone_id", request.getZoneId(),
                "active_drivers", request.getActiveDrivers(),
                "pending_rides", request.getPendingRides(),
                "message", "Demand/supply metrics cached successfully."
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/estimate/{estimateId}")
    @Operation(
            summary = "Get estimate by ID",
            description = "Retrieve a fare estimate by its ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estimate found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FareEstimateResponse.class))),
            @ApiResponse(responseCode = "404", description = "Estimate not found")
    })
    public ResponseEntity<?> getEstimateById(
            @Parameter(description = "Estimate ID", required = true)
            @PathVariable String estimateId) {

        log.info("Received get estimate request - estimateId: {}", estimateId);
        Optional<FareEstimate> estimate = pricingService.getEstimateById(estimateId);

        if (estimate.isPresent()) {
            return ResponseEntity.ok(FareEstimateResponse.fromFareEstimate(estimate.get()));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "ESTIMATE_NOT_FOUND",
                    "message", "Estimate with ID " + estimateId + " not found",
                    "estimateId", estimateId
            ));
        }
    }

    @DeleteMapping("/estimate/{estimateId}")
    @Operation(
            summary = "Cancel estimate",
            description = "Cancel a pending estimate before it expires"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estimate cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Estimate cannot be cancelled (not in PENDING status or already expired)"),
            @ApiResponse(responseCode = "404", description = "Estimate not found")
    })
    public ResponseEntity<Map<String, Object>> cancelEstimate(
            @Parameter(description = "Estimate ID to cancel", required = true)
            @PathVariable String estimateId) {

        log.info("Received cancel estimate request - estimateId: {}", estimateId);
        return pricingService.cancelEstimate(estimateId);
    }

    @PostMapping("/surge/compute/{zoneId}")
    @Operation(
            summary = "Compute surge multiplier for zone",
            description = "Manually trigger surge multiplier calculation for a specific zone based on current demand/supply metrics"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Surge computed successfully"),
            @ApiResponse(responseCode = "400", description = "Zone not found or computation failed")
    })
    public ResponseEntity<Map<String, Object>> computeSurge(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId,

            @Parameter(description = "Include bad weather flag (optional)")
            @RequestParam(required = false, defaultValue = "false") Boolean badWeather) {

        log.info("Received surge compute request - zoneId: {}, badWeather: {}", zoneId, badWeather);
        try {
            SurgePricingService.SurgeComputationResult result = surgePricingService.computeSurgeFromRules(zoneId, badWeather, java.time.LocalTime.now());
            Map<String, Object> response = Map.of(
                    "zone_id", zoneId,
                    "previous_multiplier", result.previousMultiplier(),
                    "predicted_multiplier", result.predictedMultiplier(),
                    "updated", result.updated(),
                    "message", result.updated() ? "Surge multiplier updated" : "Surge multiplier unchanged (below threshold)"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to compute surge for zone {}: {}", zoneId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "zone_id", zoneId,
                    "error", "COMPUTATION_FAILED",
                    "message", "Failed to compute surge for zone: " + zoneId
            ));
        }
    }

    @GetMapping("/zones/{zoneId}/metrics")
    @Operation(
            summary = "Get zone metrics",
            description = "Retrieve current demand/supply metrics for a specific zone"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Zone metrics retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No metrics found for zone")
    })
    public ResponseEntity<Map<String, Object>> getZoneMetrics(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId) {

        log.info("Received zone metrics request - zoneId: {}", zoneId);
        return surgePricingService.getCurrentZoneMetrics(zoneId)
                .map(metrics -> ResponseEntity.ok(Map.<String, Object>of(
                        "zone_id", metrics.zoneId(),
                        "active_drivers", metrics.activeDrivers(),
                        "pending_rides", metrics.pendingRides(),
                        "updated_at", metrics.updatedAt().toString(),
                        "demand_ratio", metrics.pendingRides() > 0 && metrics.activeDrivers() > 0
                                ? String.format("%.2f", (double) metrics.pendingRides() / metrics.activeDrivers())
                                : "N/A"
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "zone_id", zoneId,
                        "error", "METRICS_NOT_FOUND",
                        "message", "No metrics found for zone: " + zoneId
                )));
    }

    @GetMapping("/estimates")
    @Operation(
            summary = "List estimates with filters",
            description = "List fare estimates with optional filters for status, vehicle type, and zone"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estimates retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> listEstimates(
            @Parameter(description = "Filter by status (PENDING, CONFIRMED, EXPIRED, CANCELLED)")
            @RequestParam(required = false) String status,

            @Parameter(description = "Filter by vehicle type (BIKE, CAR4, CAR7)")
            @RequestParam(required = false) String vehicleType,

            @Parameter(description = "Filter by pickup zone")
            @RequestParam(required = false) String pickupZone,

            @Parameter(description = "Limit number of results (default 50)")
            @RequestParam(required = false, defaultValue = "50") Integer limit,

            @Parameter(description = "Offset for pagination")
            @RequestParam(required = false, defaultValue = "0") Integer offset) {

        log.info("Received list estimates request - status: {}, vehicleType: {}, pickupZone: {}, limit: {}, offset: {}",
                status, vehicleType, pickupZone, limit, offset);

        List<FareEstimate> estimates = pricingService.listEstimates(status, vehicleType, pickupZone, limit, offset);
        List<FareEstimateResponse> responses = estimates.stream()
                .map(FareEstimateResponse::fromFareEstimate)
                .toList();

        return ResponseEntity.ok(Map.of(
                "estimates", responses,
                "count", responses.size(),
                "limit", limit,
                "offset", offset
        ));
    }
}
