package iuh.fit.payment_service.dto.response;

import iuh.fit.payment_service.enums.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayChargeResponse {

    private boolean success;
    private boolean pending;
    private String gatewayTransactionId;
    private String status;
    private String message;
    private String errorCode;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private String transactionRef;
    private String payUrl;
    private String qrCodeUrl;
    private String deeplink;
    private String deeplinkWallet;
    private String momoOrderId;
    private String momoRequestId;
    private String zaloPayAppTransId;
    private String zaloPayOrderToken;
}
