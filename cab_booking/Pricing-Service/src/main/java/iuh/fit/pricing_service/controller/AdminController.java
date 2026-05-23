package iuh.fit.pricing_service.controller;

import iuh.fit.pricing_service.dto.ApiResponse;
import iuh.fit.pricing_service.dto.BulkZoneMetricsRequest;
import iuh.fit.pricing_service.dto.DashboardResponse;
import iuh.fit.pricing_service.dto.PricingConfigRequest;
import iuh.fit.pricing_service.dto.PricingConfigResponse;
import iuh.fit.pricing_service.dto.SurgeRuleRequest;
import iuh.fit.pricing_service.dto.SurgeRuleResponse;
import iuh.fit.pricing_service.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Admin API", description = "Admin APIs for managing pricing configurations and surge rules")
public class AdminController {

    private final AdminService adminService;

    // ==================== PRICING CONFIG ENDPOINTS ====================

    @PostMapping("/pricing-configs")
    @Operation(summary = "Create pricing config", description = "Create a new pricing configuration for a vehicle type")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Pricing config created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or config already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PricingConfigResponse>> createPricingConfig(
            @RequestBody @Valid PricingConfigRequest request) {
        log.info("Admin creating pricing config for vehicle type: {}", request.getVehicleType());
        PricingConfigResponse created = adminService.createPricingConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pricing config created successfully", created));
    }

    @PutMapping("/pricing-configs/{id}")
    @Operation(summary = "Update pricing config", description = "Update an existing pricing configuration by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing config updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pricing config not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PricingConfigResponse>> updatePricingConfig(
            @Parameter(description = "Pricing config ID", required = true)
            @PathVariable String id,
            @RequestBody @Valid PricingConfigRequest request) {
        log.info("Admin updating pricing config: {}", id);
        PricingConfigResponse updated = adminService.updatePricingConfig(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Pricing config updated successfully", updated));
    }

    @GetMapping("/pricing-configs/{id}")
    @Operation(summary = "Get pricing config by ID", description = "Retrieve a pricing configuration by its ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing config found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pricing config not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PricingConfigResponse>> getPricingConfigById(
            @Parameter(description = "Pricing config ID", required = true)
            @PathVariable String id) {
        log.info("Admin retrieving pricing config: {}", id);
        PricingConfigResponse config = adminService.getPricingConfigById(id);
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @GetMapping("/pricing-configs/vehicle/{vehicleType}")
    @Operation(summary = "Get pricing config by vehicle type", description = "Retrieve a pricing configuration by vehicle type")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing config found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pricing config not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PricingConfigResponse>> getPricingConfigByVehicleType(
            @Parameter(description = "Vehicle type (BIKE, CAR4, CAR7)", required = true)
            @PathVariable String vehicleType) {
        log.info("Admin retrieving pricing config for vehicle: {}", vehicleType);
        PricingConfigResponse config = adminService.getPricingConfigByVehicleType(vehicleType);
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @GetMapping("/pricing-configs")
    @Operation(summary = "Get all pricing configs", description = "Retrieve all pricing configurations with optional active filter")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing configs retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<List<PricingConfigResponse>>> getAllPricingConfigs(
            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean active) {
        log.info("Admin retrieving all pricing configs, active filter: {}", active);
        List<PricingConfigResponse> configs;
        if (Boolean.TRUE.equals(active)) {
            configs = adminService.getActivePricingConfigs();
        } else {
            configs = adminService.getAllPricingConfigs();
        }
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @DeleteMapping("/pricing-configs/{id}")
    @Operation(summary = "Delete pricing config", description = "Delete a pricing configuration by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing config deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pricing config not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Void>> deletePricingConfig(
            @Parameter(description = "Pricing config ID", required = true)
            @PathVariable String id) {
        log.info("Admin deleting pricing config: {}", id);
        adminService.deletePricingConfig(id);
        return ResponseEntity.ok(ApiResponse.ok("Pricing config deleted successfully"));
    }

    @PatchMapping("/pricing-configs/{id}/toggle")
    @Operation(summary = "Toggle pricing config status", description = "Toggle the active/inactive status of a pricing configuration")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pricing config status toggled successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pricing config not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PricingConfigResponse>> togglePricingConfigStatus(
            @Parameter(description = "Pricing config ID", required = true)
            @PathVariable String id) {
        log.info("Admin toggling pricing config status: {}", id);
        PricingConfigResponse updated = adminService.togglePricingConfigStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Pricing config status toggled successfully", updated));
    }

    // ==================== SURGE RULE ENDPOINTS ====================

    @PostMapping("/surge-rules")
    @Operation(summary = "Create surge rule", description = "Create a new surge rule for a zone")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Surge rule created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or rule already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<SurgeRuleResponse>> createSurgeRule(
            @RequestBody @Valid SurgeRuleRequest request) {
        log.info("Admin creating surge rule for zone: {}", request.getZoneId());
        SurgeRuleResponse created = adminService.createSurgeRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Surge rule created successfully", created));
    }

    @PutMapping("/surge-rules/{id}")
    @Operation(summary = "Update surge rule", description = "Update an existing surge rule by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rule updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Surge rule not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<SurgeRuleResponse>> updateSurgeRule(
            @Parameter(description = "Surge rule ID", required = true)
            @PathVariable String id,
            @RequestBody @Valid SurgeRuleRequest request) {
        log.info("Admin updating surge rule: {}", id);
        SurgeRuleResponse updated = adminService.updateSurgeRule(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Surge rule updated successfully", updated));
    }

    @GetMapping("/surge-rules/{id}")
    @Operation(summary = "Get surge rule by ID", description = "Retrieve a surge rule by its ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rule found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Surge rule not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<SurgeRuleResponse>> getSurgeRuleById(
            @Parameter(description = "Surge rule ID", required = true)
            @PathVariable String id) {
        log.info("Admin retrieving surge rule: {}", id);
        SurgeRuleResponse rule = adminService.getSurgeRuleById(id);
        return ResponseEntity.ok(ApiResponse.ok(rule));
    }

    @GetMapping("/surge-rules/zone/{zoneId}")
    @Operation(summary = "Get surge rule by zone", description = "Retrieve a surge rule by zone ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rule found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Surge rule not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<SurgeRuleResponse>> getSurgeRuleByZoneId(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId) {
        log.info("Admin retrieving surge rule for zone: {}", zoneId);
        SurgeRuleResponse rule = adminService.getSurgeRuleByZoneId(zoneId);
        return ResponseEntity.ok(ApiResponse.ok(rule));
    }

    @GetMapping("/surge-rules")
    @Operation(summary = "Get all surge rules", description = "Retrieve all surge rules")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rules retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<List<SurgeRuleResponse>>> getAllSurgeRules() {
        log.info("Admin retrieving all surge rules");
        List<SurgeRuleResponse> rules = adminService.getAllSurgeRules();
        return ResponseEntity.ok(ApiResponse.ok(rules));
    }

    @DeleteMapping("/surge-rules/{id}")
    @Operation(summary = "Delete surge rule", description = "Delete a surge rule by ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rule deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Surge rule not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Void>> deleteSurgeRule(
            @Parameter(description = "Surge rule ID", required = true)
            @PathVariable String id) {
        log.info("Admin deleting surge rule: {}", id);
        adminService.deleteSurgeRule(id);
        return ResponseEntity.ok(ApiResponse.ok("Surge rule deleted successfully"));
    }

    @DeleteMapping("/surge-rules/zone/{zoneId}")
    @Operation(summary = "Delete surge rule by zone", description = "Delete a surge rule by zone ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Surge rule deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Surge rule not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Void>> deleteSurgeRuleByZoneId(
            @Parameter(description = "Zone ID", required = true)
            @PathVariable String zoneId) {
        log.info("Admin deleting surge rule for zone: {}", zoneId);
        adminService.deleteSurgeRuleByZoneId(zoneId);
        return ResponseEntity.ok(ApiResponse.ok("Surge rule deleted successfully"));
    }

    // ==================== BULK OPERATIONS ====================

    @PostMapping("/surge-rules/bulk")
    @Operation(summary = "Create bulk surge rules", description = "Create multiple surge rules in a single request")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Surge rules created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<List<SurgeRuleResponse>>> createBulkSurgeRules(
            @RequestBody @Valid List<SurgeRuleRequest> requests) {
        log.info("Admin creating {} surge rules in bulk", requests.size());
        List<SurgeRuleResponse> created = adminService.createBulkSurgeRules(requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Surge rules created successfully", created));
    }

    @PostMapping("/zone-metrics/bulk")
    @Operation(summary = "Update bulk zone metrics", description = "Update demand/supply metrics for multiple zones in a single request")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Zone metrics updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Void>> updateBulkZoneMetrics(
            @RequestBody @Valid BulkZoneMetricsRequest request) {
        log.info("Admin updating bulk zone metrics for {} zones", request.getZoneMetrics().size());
        adminService.updateBulkZoneMetrics(request);
        return ResponseEntity.ok(ApiResponse.ok("Zone metrics updated successfully"));
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard", description = "Retrieve a summary dashboard with pricing config and surge rule statistics")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dashboard retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        log.info("Admin retrieving dashboard");
        DashboardResponse dashboard = adminService.getDashboard();
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }
}
