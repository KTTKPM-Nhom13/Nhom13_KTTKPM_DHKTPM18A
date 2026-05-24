package iuh.fit.pricing_service.dto;

import iuh.fit.pricing_service.model.PromoCode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class PromoCodeRequest {

    @NotBlank(message = "Promo code is required")
    private String code;

    private String description;

    @NotNull(message = "Discount type is required")
    private PromoCode.PromoCodeType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be positive")
    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount;

    private BigDecimal minimumBookingAmount;

    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiryDate;

    private Integer usageLimit;

    @Builder.Default
    private boolean active = true;
}
