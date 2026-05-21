package iuh.fit.payment_service.dto.vnpay;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VnPayCallbackResult {

    private boolean success;
    private String transactionId;
    private String gatewayTransactionId;
    private BigDecimal amount;
    private String responseCode;
    private String transactionStatus;
    private String message;
}
