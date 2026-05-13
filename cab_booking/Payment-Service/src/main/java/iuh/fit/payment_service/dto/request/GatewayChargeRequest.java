package iuh.fit.payment_service.dto.request;

import iuh.fit.payment_service.enums.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayChargeRequest {

    private String transactionId;
    private String customerId;
    private String bookingId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private String cardToken;
    private String walletId;
    private String description;
}
