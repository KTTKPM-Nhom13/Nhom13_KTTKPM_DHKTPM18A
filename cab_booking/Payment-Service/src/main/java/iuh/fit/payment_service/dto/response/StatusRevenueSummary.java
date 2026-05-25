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
public class StatusRevenueSummary {
    private String status;
    private BigDecimal totalAmount;
    private Long transactionsCount;
}
