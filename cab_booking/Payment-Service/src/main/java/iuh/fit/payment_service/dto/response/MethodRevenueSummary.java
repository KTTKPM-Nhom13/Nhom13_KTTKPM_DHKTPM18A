package iuh.fit.payment_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodRevenueSummary {
    private String paymentMethod;
    private BigDecimal totalRevenue;
    private Long transactionsCount;
    private BigDecimal percentage;
}
