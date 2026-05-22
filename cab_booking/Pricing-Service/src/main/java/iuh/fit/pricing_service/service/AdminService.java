package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.dto.*;
import iuh.fit.pricing_service.exception.ResourceNotFoundException;
import iuh.fit.pricing_service.model.PricingConfig;
import iuh.fit.pricing_service.model.SurgeRule;
import iuh.fit.pricing_service.repository.PricingConfigRepository;
import iuh.fit.pricing_service.repository.SurgeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final PricingConfigRepository pricingConfigRepository;
    private final SurgeRuleRepository surgeRuleRepository;
    private final SurgePricingService surgePricingService;
    private final ZoneService zoneService;
    private final ZoneMetricsService zoneMetricsService;

    // ==================== PRICING CONFIG CRUD ====================

    public PricingConfigResponse createPricingConfig(PricingConfigRequest request) {
        if (pricingConfigRepository.findByVehicleType(request.getVehicleType().toUpperCase()).isPresent()) {
            throw new IllegalStateException("Pricing config for vehicle type '" + request.getVehicleType() + "' already exists");
        }

        PricingConfig config = PricingConfig.builder()
                .vehicleType(request.getVehicleType().toUpperCase())
                .baseFare(request.getBaseFare())
                .perKmRate(request.getPerKmRate())
                .perMinuteRate(request.getPerMinuteRate())
                .multiplier(request.getMultiplier() != null ? request.getMultiplier() : 1.0)
                .active(request.getActive() != null ? request.getActive() : true)
                .updatedAt(LocalDateTime.now())
                .schemaVersion("1.0.0")
                .build();

        PricingConfig saved = pricingConfigRepository.save(config);
        log.info("Created pricing config for vehicle type: {}", saved.getVehicleType());
        return toPricingConfigResponse(saved);
    }

    public PricingConfigResponse updatePricingConfig(String id, PricingConfigRequest request) {
        PricingConfig existing = pricingConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing config not found with id: " + id));

        existing.setBaseFare(request.getBaseFare());
        existing.setPerKmRate(request.getPerKmRate());
        existing.setPerMinuteRate(request.getPerMinuteRate());
        existing.setMultiplier(request.getMultiplier() != null ? request.getMultiplier() : existing.getMultiplier());
        existing.setActive(request.getActive() != null ? request.getActive() : existing.getActive());
        existing.setUpdatedAt(LocalDateTime.now());

        PricingConfig saved = pricingConfigRepository.save(existing);
        log.info("Updated pricing config {} for vehicle type: {}", saved.getId(), saved.getVehicleType());
        return toPricingConfigResponse(saved);
    }

    public PricingConfigResponse getPricingConfigById(String id) {
        PricingConfig config = pricingConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing config not found with id: " + id));
        return toPricingConfigResponse(config);
    }

    public PricingConfigResponse getPricingConfigByVehicleType(String vehicleType) {
        PricingConfig config = pricingConfigRepository.findByVehicleType(vehicleType.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Pricing config not found for vehicle type: " + vehicleType));
        return toPricingConfigResponse(config);
    }

    public List<PricingConfigResponse> getAllPricingConfigs() {
        return pricingConfigRepository.findAll().stream()
                .map(this::toPricingConfigResponse)
                .collect(Collectors.toList());
    }

    public List<PricingConfigResponse> getActivePricingConfigs() {
        return pricingConfigRepository.findByActive(true).stream()
                .map(this::toPricingConfigResponse)
                .collect(Collectors.toList());
    }

    public void deletePricingConfig(String id) {
        if (!pricingConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing config not found with id: " + id);
        }
        pricingConfigRepository.deleteById(id);
        log.info("Deleted pricing config with id: {}", id);
    }

    public PricingConfigResponse togglePricingConfigStatus(String id) {
        PricingConfig config = pricingConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing config not found with id: " + id));
        config.setActive(!config.getActive());
        config.setUpdatedAt(LocalDateTime.now());
        PricingConfig saved = pricingConfigRepository.save(config);
        log.info("Toggled pricing config {} active status to: {}", id, saved.getActive());
        return toPricingConfigResponse(saved);
    }

    // ==================== SURGE RULE CRUD ====================

    public SurgeRuleResponse createSurgeRule(SurgeRuleRequest request) {
        String zoneId = request.getZoneId();
        if (zoneId == null || zoneId.isBlank()) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                throw new IllegalArgumentException(
                        "Either 'zoneId' OR both 'latitude' and 'longitude' must be provided to create a surge rule.");
            }
            zoneId = zoneService.determineZone(request.getLatitude(), request.getLongitude());
            log.info("Zone ID auto-generated from coordinates: {}", zoneId);
        }

        if (surgeRuleRepository.findByZoneId(zoneId).isPresent()) {
            throw new IllegalStateException(
                    "Surge rule for zone '" + zoneId + "' already exists. Use PUT to update or choose a different zoneId.");
        }

        BigDecimal multiplier = BigDecimal.valueOf(request.getSurgeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        SurgeRule rule = SurgeRule.builder()
                .zoneId(zoneId)
                .zoneName(request.getZoneName())
                .surgeMultiplier(multiplier)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusKm(request.getRadiusKm())
                .activeDrivers(request.getActiveDrivers())
                .pendingRides(request.getPendingRides())
                .minMultiplier(request.getMinMultiplier() != null ? BigDecimal.valueOf(request.getMinMultiplier()) : null)
                .maxMultiplier(request.getMaxMultiplier() != null ? BigDecimal.valueOf(request.getMaxMultiplier()) : null)
                .source(request.getSource() != null ? request.getSource() : SurgeRule.SurgeSource.MANUAL.name())
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .schemaVersion("1.0.0")
                .build();

        SurgeRule saved = surgeRuleRepository.save(rule);
        surgePricingService.cacheSurgeMultiplier(zoneId, multiplier);
        zoneMetricsService.syncMetricsForZone(zoneId);
        log.info("Created new surge rule for zone: {} with multiplier: {}", zoneId, multiplier);
        return toSurgeRuleResponse(saved);
    }

    public SurgeRuleResponse updateSurgeRule(String id, SurgeRuleRequest request) {
        SurgeRule existing = surgeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surge rule not found with id: " + id));

        if (request.getZoneId() != null && !request.getZoneId().isBlank()) {
            existing.setZoneId(request.getZoneId());
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            existing.setZoneId(zoneService.determineZone(request.getLatitude(), request.getLongitude()));
        }
        if (request.getZoneName() != null) {
            existing.setZoneName(request.getZoneName());
        }
        if (request.getSurgeMultiplier() != null) {
            existing.setSurgeMultiplier(BigDecimal.valueOf(request.getSurgeMultiplier())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getLatitude() != null) {
            existing.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            existing.setLongitude(request.getLongitude());
        }
        if (request.getRadiusKm() != null) {
            existing.setRadiusKm(request.getRadiusKm());
        }
        if (request.getActiveDrivers() != null) {
            existing.setActiveDrivers(request.getActiveDrivers());
        }
        if (request.getPendingRides() != null) {
            existing.setPendingRides(request.getPendingRides());
        }
        if (request.getMinMultiplier() != null) {
            existing.setMinMultiplier(BigDecimal.valueOf(request.getMinMultiplier()));
        }
        if (request.getMaxMultiplier() != null) {
            existing.setMaxMultiplier(BigDecimal.valueOf(request.getMaxMultiplier()));
        }
        existing.setLastUpdated(LocalDateTime.now());
        existing.setSource(SurgeRule.SurgeSource.MANUAL.name());

        SurgeRule saved = surgeRuleRepository.save(existing);
        surgePricingService.cacheSurgeMultiplier(existing.getZoneId(), saved.getSurgeMultiplier());
        zoneMetricsService.syncMetricsForZone(existing.getZoneId());
        log.info("Updated surge rule {} for zone: {}", id, saved.getZoneId());
        return toSurgeRuleResponse(saved);
    }

    public SurgeRuleResponse getSurgeRuleById(String id) {
        SurgeRule rule = surgeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surge rule not found with id: " + id));
        return toSurgeRuleResponse(rule);
    }

    public SurgeRuleResponse getSurgeRuleByZoneId(String zoneId) {
        SurgeRule rule = surgeRuleRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Surge rule not found for zone: " + zoneId));
        return toSurgeRuleResponse(rule);
    }

    public List<SurgeRuleResponse> getAllSurgeRules() {
        return surgeRuleRepository.findAll().stream()
                .map(this::toSurgeRuleResponse)
                .collect(Collectors.toList());
    }

    public void deleteSurgeRule(String id) {
        SurgeRule rule = surgeRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surge rule not found with id: " + id));
        String zoneId = rule.getZoneId();
        surgeRuleRepository.delete(rule);
        surgePricingService.invalidateSurgeCache(zoneId);
        surgePricingService.removeFromActiveZones(zoneId);
        log.info("Deleted surge rule {} for zone: {}", id, zoneId);
    }

    public void deleteSurgeRuleByZoneId(String zoneId) {
        surgeRuleRepository.deleteByZoneId(zoneId);
        surgePricingService.invalidateSurgeCache(zoneId);
        surgePricingService.removeFromActiveZones(zoneId);
        log.info("Deleted surge rule for zone: {}", zoneId);
    }

    // ==================== BULK OPERATIONS ====================

    public void updateBulkZoneMetrics(BulkZoneMetricsRequest request) {
        for (BulkZoneMetricsRequest.ZoneMetricEntry entry : request.getZoneMetrics()) {
            surgePricingService.updateCurrentZoneMetrics(
                    entry.getZoneId(),
                    entry.getActiveDrivers(),
                    entry.getPendingRides()
            );
        }
        log.info("Updated bulk zone metrics for {} zones", request.getZoneMetrics().size());
    }

    public List<SurgeRuleResponse> createBulkSurgeRules(List<SurgeRuleRequest> requests) {
        List<SurgeRuleResponse> results = requests.stream()
                .map(this::createSurgeRule)
                .collect(Collectors.toList());
        zoneMetricsService.syncAllActiveZones();
        return results;
    }

    // ==================== DASHBOARD ====================

    public DashboardResponse getDashboard() {
        int totalConfigs = (int) pricingConfigRepository.count();
        int totalSurgeRules = (int) surgeRuleRepository.count();
        int activeZones = surgePricingService.getZonesWithCurrentMetrics().size();

        Map<String, Object> configSummary = new HashMap<>();
        List<PricingConfig> allConfigs = pricingConfigRepository.findAll();
        configSummary.put("vehicleTypes", allConfigs.stream()
                .map(PricingConfig::getVehicleType)
                .collect(Collectors.toList()));
        configSummary.put("baseFareRange", Map.of(
                "min", allConfigs.stream().mapToDouble(PricingConfig::getBaseFare).min().orElse(0),
                "max", allConfigs.stream().mapToDouble(PricingConfig::getBaseFare).max().orElse(0)
        ));

        Map<String, Object> surgeSummary = new HashMap<>();
        List<SurgeRule> allRules = surgeRuleRepository.findAll();
        surgeSummary.put("zones", allRules.stream()
                .map(SurgeRule::getZoneId)
                .collect(Collectors.toList()));
        surgeSummary.put("multiplierRange", Map.of(
                "min", allRules.stream()
                        .map(SurgeRule::getSurgeMultiplier)
                        .filter(m -> m != null)
                        .mapToDouble(BigDecimal::doubleValue).min().orElse(1.0),
                "max", allRules.stream()
                        .map(SurgeRule::getSurgeMultiplier)
                        .filter(m -> m != null)
                        .mapToDouble(BigDecimal::doubleValue).max().orElse(1.0)
        ));
        surgeSummary.put("avgMultiplier", allRules.stream()
                .map(SurgeRule::getSurgeMultiplier)
                .filter(m -> m != null)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(1.0));

        return DashboardResponse.builder()
                .totalPricingConfigs(totalConfigs)
                .totalSurgeRules(totalSurgeRules)
                .activeZones(activeZones)
                .configSummary(configSummary)
                .surgeSummary(surgeSummary)
                .build();
    }

    // ==================== MAPPERS ====================

    private PricingConfigResponse toPricingConfigResponse(PricingConfig config) {
        return PricingConfigResponse.builder()
                .id(config.getId())
                .vehicleType(config.getVehicleType())
                .baseFare(config.getBaseFare())
                .perKmRate(config.getPerKmRate())
                .perMinuteRate(config.getPerMinuteRate())
                .multiplier(config.getMultiplier())
                .active(config.getActive())
                .updatedAt(config.getUpdatedAt())
                .schemaVersion(config.getSchemaVersion())
                .build();
    }

    private SurgeRuleResponse toSurgeRuleResponse(SurgeRule rule) {
        return SurgeRuleResponse.builder()
                .id(rule.getId())
                .zoneId(rule.getZoneId())
                .zoneName(rule.getZoneName())
                .surgeMultiplier(rule.getSurgeMultiplier())
                .latitude(rule.getLatitude())
                .longitude(rule.getLongitude())
                .radiusKm(rule.getRadiusKm())
                .activeDrivers(rule.getActiveDrivers())
                .pendingRides(rule.getPendingRides())
                .demandScore(rule.getDemandScore())
                .minMultiplier(rule.getMinMultiplier())
                .maxMultiplier(rule.getMaxMultiplier())
                .lastUpdated(rule.getLastUpdated())
                .createdAt(rule.getCreatedAt())
                .source(rule.getSource())
                .schemaVersion(rule.getSchemaVersion())
                .build();
    }
}
