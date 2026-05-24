package iuh.fit.pricing_service.controller;

import iuh.fit.pricing_service.dto.ApiResponse;
import iuh.fit.pricing_service.dto.PromoCodeRequest;
import iuh.fit.pricing_service.dto.PromoCodeResponse;
import iuh.fit.pricing_service.service.PromoCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/promo-codes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Promo Code Admin API", description = "Admin APIs for managing promo codes")
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping
    @Operation(summary = "Create promo code")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> createPromoCode(@RequestBody @Valid PromoCodeRequest request) {
        log.info("Admin creating promo code: {}", request.getCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Promo code created successfully", promoCodeService.createPromoCode(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update promo code")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> updatePromoCode(@PathVariable String id, @RequestBody @Valid PromoCodeRequest request) {
        log.info("Admin updating promo code: {}", id);
        return ResponseEntity.ok(ApiResponse.ok("Promo code updated successfully", promoCodeService.updatePromoCode(id, request)));
    }

    @GetMapping
    @Operation(summary = "Get all promo codes")
    public ResponseEntity<ApiResponse<List<PromoCodeResponse>>> getAllPromoCodes() {
        return ResponseEntity.ok(ApiResponse.ok(promoCodeService.getAllPromoCodes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get promo code by ID")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> getPromoCodeById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(promoCodeService.getPromoCodeById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete promo code")
    public ResponseEntity<ApiResponse<Void>> deletePromoCode(@PathVariable String id) {
        log.info("Admin deleting promo code: {}", id);
        promoCodeService.deletePromoCode(id);
        return ResponseEntity.ok(ApiResponse.ok("Promo code deleted successfully", null));
    }
}
