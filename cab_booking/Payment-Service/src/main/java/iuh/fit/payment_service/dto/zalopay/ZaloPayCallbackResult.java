package iuh.fit.payment_service.dto.zalopay;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCallbackResult {

    private boolean success;
    private String appTransId;
    private String transactionId;
    private String zpTransId;
    private BigDecimal amount;
    private String message;
}
