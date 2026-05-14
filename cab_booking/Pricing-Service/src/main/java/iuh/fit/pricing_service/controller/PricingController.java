package iuh.fit.pricing_service.controller;

import iuh.fit.pricing_service.model.FareEstimate;
import iuh.fit.pricing_service.model.FareEstimateResponse;
import iuh.fit.pricing_service.model.PricingTestRequest;
import iuh.fit.pricing_service.model.PricingTestResponse;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Pricing API", description = "APIs for fare estimation and surge pricing management")
public class PricingController {

    private final PricingService pricingService;
    private final SurgePricingService surgePricingService;

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

    @GetMapping("/estimate")
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
            @Parameter(description = "Pickup location latitude", required = true)
            @RequestParam @NotNull @Min(-90) @Max(90) Double pickupLat,

            @Parameter(description = "Pickup location longitude", required = true)
            @RequestParam @NotNull @Min(-180) @Max(180) Double pickupLng,

            @Parameter(description = "Dropoff location latitude", required = true)
            @RequestParam @NotNull @Min(-90) @Max(90) Double dropoffLat,

            @Parameter(description = "Dropoff location longitude", required = true)
            @RequestParam @NotNull @Min(-180) @Max(180) Double dropoffLng,

            @Parameter(description = "Vehicle type (ECONOMY, COMFORT, PREMIUM)", required = true)
            @RequestParam @NotBlank String vehicleType,

            @Parameter(description = "Estimated duration in minutes (optional)")
            @RequestParam(required = false) Integer estimatedDurationMinutes) {

        log.info("Received fare estimate request - pickup: ({}, {}), dropoff: ({}, {}), vehicle: {}, duration: {}",
                pickupLat, pickupLng, dropoffLat, dropoffLng, vehicleType, estimatedDurationMinutes);

        var request = iuh.fit.pricing_service.model.FareEstimateRequest.builder()
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropoffLat(dropoffLat)
                .dropoffLng(dropoffLng)
                .vehicleType(vehicleType)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .build();

        FareEstimateResponse response = pricingService.calculateFareEstimate(request);

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
            @PathVariable String estimateId) {

        log.info("Received confirm estimate request - estimateId: {}", estimateId);
        FareEstimate confirmed = pricingService.confirmFare(estimateId);
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

    @PostMapping("/surge/{zoneId}")
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

            @Parameter(description = "New surge multiplier value", required = true)
            @RequestParam @NotNull BigDecimal multiplier) {

        log.info("Received surge update request - zoneId: {}, multiplier: {}", zoneId, multiplier);
        pricingService.updateSurgeForZone(zoneId, multiplier);

        Map<String, Object> response = Map.of(
                "zone_id", zoneId,
                "surge_multiplier", multiplier,
                "message", "Surge multiplier updated and event published"
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
            @RequestParam @NotBlank String zoneId,
            @RequestParam @NotNull Integer activeDrivers,
            @RequestParam @NotNull Integer pendingRides) {

        log.info("Received demand/supply update - zoneId: {}, drivers: {}, rides: {}",
                zoneId, activeDrivers, pendingRides);
        pricingService.processDemandSupplyUpdate(zoneId, activeDrivers, pendingRides);

        Map<String, Object> response = Map.of(
                "zone_id", zoneId,
                "active_drivers", activeDrivers,
                "pending_rides", pendingRides,
                "message", "Demand/supply metrics cached. Surge update is handled asynchronously by scheduler."
        );

        return ResponseEntity.ok(response);
    }
}
