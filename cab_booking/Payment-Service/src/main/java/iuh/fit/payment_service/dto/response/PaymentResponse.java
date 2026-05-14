package iuh.fit.payment_service.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static PaymentResponse fromEntity(PaymentTransaction entity) {
        PaymentResponse response = PaymentResponse.builder()
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
        parseGatewayResponse(response, entity.getGatewayResponseJson());
        return response;
    }

    public static PaymentResponse fromEntity(PaymentTransaction entity, String message) {
        PaymentResponse response = fromEntity(entity);
        response.setMessage(message);
        return response;
    }

    private static void parseGatewayResponse(PaymentResponse response, String gatewayResponseJson) {
        if (gatewayResponseJson == null || gatewayResponseJson.isBlank()) {
            return;
        }
        try {
            GatewayChargeResponse gatewayResponse = objectMapper.readValue(gatewayResponseJson, GatewayChargeResponse.class);
            if (gatewayResponse.getPayUrl() != null) {
                response.setPayUrl(gatewayResponse.getPayUrl());
            }
            if (gatewayResponse.getQrCodeUrl() != null) {
                response.setQrCodeUrl(gatewayResponse.getQrCodeUrl());
            }
            if (gatewayResponse.getDeeplink() != null) {
                response.setDeeplink(gatewayResponse.getDeeplink());
            }
            if (gatewayResponse.getDeeplinkWallet() != null) {
                response.setDeeplinkWallet(gatewayResponse.getDeeplinkWallet());
            }
            if (gatewayResponse.getMomoOrderId() != null) {
                response.setMomoOrderId(gatewayResponse.getMomoOrderId());
            }
            if (gatewayResponse.getMomoRequestId() != null) {
                response.setMomoRequestId(gatewayResponse.getMomoRequestId());
            }
            if (gatewayResponse.getZaloPayAppTransId() != null) {
                response.setZaloPayAppTransId(gatewayResponse.getZaloPayAppTransId());
            }
            if (gatewayResponse.getZaloPayOrderToken() != null) {
                response.setZaloPayOrderToken(gatewayResponse.getZaloPayOrderToken());
            }
        } catch (JsonProcessingException e) {
            // Log and ignore - entity fields are already populated
        }
    }
}
