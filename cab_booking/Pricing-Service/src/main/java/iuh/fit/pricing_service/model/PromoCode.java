package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "promo_codes")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromoCode {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String description;

    private PromoCodeType discountType; // FIXED or PERCENTAGE

    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount;

    private BigDecimal minimumBookingAmount;

    private LocalDateTime expiryDate;

    private Integer usageLimit;

    private Integer usedCount;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum PromoCodeType {
        FIXED,
        PERCENTAGE
    }
}
