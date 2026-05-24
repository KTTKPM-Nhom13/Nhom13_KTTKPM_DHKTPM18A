package iuh.fit.pricing_service.dto;

import iuh.fit.pricing_service.model.PromoCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromoCodeResponse {
    private String id;
    private String code;
    private String description;
    private PromoCode.PromoCodeType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minimumBookingAmount;
    private LocalDateTime expiryDate;
    private Integer usageLimit;
    private Integer usedCount;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PromoCodeResponse from(PromoCode promoCode) {
        return PromoCodeResponse.builder()
                .id(promoCode.getId())
                .code(promoCode.getCode())
                .description(promoCode.getDescription())
                .discountType(promoCode.getDiscountType())
                .discountValue(promoCode.getDiscountValue())
                .maxDiscountAmount(promoCode.getMaxDiscountAmount())
                .minimumBookingAmount(promoCode.getMinimumBookingAmount())
                .expiryDate(promoCode.getExpiryDate())
                .usageLimit(promoCode.getUsageLimit())
                .usedCount(promoCode.getUsedCount())
                .active(promoCode.isActive())
                .createdAt(promoCode.getCreatedAt())
                .updatedAt(promoCode.getUpdatedAt())
                .build();
    }
}
