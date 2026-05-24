package iuh.fit.pricing_service.service;

import iuh.fit.pricing_service.dto.PromoCodeRequest;
import iuh.fit.pricing_service.dto.PromoCodeResponse;
import iuh.fit.pricing_service.exception.ResourceNotFoundException;
import iuh.fit.pricing_service.model.PromoCode;
import iuh.fit.pricing_service.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeResponse createPromoCode(PromoCodeRequest request) {
        if (promoCodeRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Promo code already exists: " + request.getCode());
        }

        PromoCode promoCode = PromoCode.builder()
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minimumBookingAmount(request.getMinimumBookingAmount())
                .expiryDate(request.getExpiryDate())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .active(request.isActive())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        PromoCode saved = promoCodeRepository.save(promoCode);
        log.info("Created promo code: {}", saved.getCode());
        return PromoCodeResponse.from(saved);
    }

    public PromoCodeResponse updatePromoCode(String id, PromoCodeRequest request) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCode", "id", id));

        promoCode.setDescription(request.getDescription());
        promoCode.setDiscountType(request.getDiscountType());
        promoCode.setDiscountValue(request.getDiscountValue());
        promoCode.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promoCode.setMinimumBookingAmount(request.getMinimumBookingAmount());
        promoCode.setExpiryDate(request.getExpiryDate());
        promoCode.setUsageLimit(request.getUsageLimit());
        promoCode.setActive(request.isActive());
        promoCode.setUpdatedAt(LocalDateTime.now());

        PromoCode saved = promoCodeRepository.save(promoCode);
        log.info("Updated promo code: {}", saved.getCode());
        return PromoCodeResponse.from(saved);
    }

    public List<PromoCodeResponse> getAllPromoCodes() {
        return promoCodeRepository.findAll().stream()
                .map(PromoCodeResponse::from)
                .collect(Collectors.toList());
    }

    public PromoCodeResponse getPromoCodeById(String id) {
        return promoCodeRepository.findById(id)
                .map(PromoCodeResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCode", "id", id));
    }

    public void deletePromoCode(String id) {
        if (!promoCodeRepository.existsById(id)) {
            throw new ResourceNotFoundException("PromoCode", "id", id);
        }
        promoCodeRepository.deleteById(id);
        log.info("Deleted promo code: {}", id);
    }

    public BigDecimal validateAndCalculateDiscount(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }

        PromoCode promoCode = promoCodeRepository.findByCode(code.toUpperCase())
                .orElse(null);

        if (promoCode == null || !promoCode.isActive()) {
            log.warn("Invalid or inactive promo code: {}", code);
            return BigDecimal.ZERO;
        }

        if (promoCode.getExpiryDate() != null && promoCode.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Promo code expired: {}", code);
            return BigDecimal.ZERO;
        }

        if (promoCode.getUsageLimit() != null && promoCode.getUsedCount() >= promoCode.getUsageLimit()) {
            log.warn("Promo code usage limit reached: {}", code);
            return BigDecimal.ZERO;
        }

        if (promoCode.getMinimumBookingAmount() != null && subtotal.compareTo(promoCode.getMinimumBookingAmount()) < 0) {
            log.warn("Booking amount {} below minimum required {} for promo code: {}", 
                    subtotal, promoCode.getMinimumBookingAmount(), code);
            return BigDecimal.ZERO;
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (promoCode.getDiscountType() == PromoCode.PromoCodeType.FIXED) {
            discount = promoCode.getDiscountValue();
        } else if (promoCode.getDiscountType() == PromoCode.PromoCodeType.PERCENTAGE) {
            discount = subtotal.multiply(promoCode.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            if (promoCode.getMaxDiscountAmount() != null && discount.compareTo(promoCode.getMaxDiscountAmount()) > 0) {
                discount = promoCode.getMaxDiscountAmount();
            }
        }

        return discount.min(subtotal); // Discount cannot exceed subtotal
    }

    public void incrementUsedCount(String code) {
        promoCodeRepository.findByCode(code.toUpperCase()).ifPresent(promo -> {
            promo.setUsedCount(promo.getUsedCount() + 1);
            promoCodeRepository.save(promo);
        });
    }
}
