package iuh.fit.payment_service.dto.response;

import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String transactionId;
    private String bookingId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String gatewayTransactionId;
    private String failureReason;
    private String idempotencyKey;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private String message;
    private String payUrl;
    private String qrCodeUrl;
    private String deeplink;
    private String deeplinkWallet;
    private String momoOrderId;
    private String momoRequestId;
    private String zaloPayAppTransId;
    private String zaloPayOrderToken;

    public static PaymentResponse fromEntity(PaymentTransaction entity) {
        return PaymentResponse.builder()
                .transactionId(entity.getTransactionId())
                .bookingId(entity.getBookingId())
                .customerId(entity.getCustomerId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .paymentMethod(entity.getPaymentMethod())
                .status(entity.getStatus())
                .gatewayTransactionId(entity.getGatewayTransactionId())
                .failureReason(entity.getFailureReason())
                .idempotencyKey(entity.getIdempotencyKey())
                .retryCount(entity.getRetryCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static PaymentResponse fromEntity(PaymentTransaction entity, String message) {
        PaymentResponse response = fromEntity(entity);
        response.setMessage(message);
        return response;
    }
}
