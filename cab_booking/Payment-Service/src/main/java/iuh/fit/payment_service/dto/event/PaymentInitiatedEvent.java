package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiatedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("paymentStatus")
    private String paymentStatus;

    @JsonProperty("paymentUrl")
    private String paymentUrl;

    @JsonProperty("payUrl")
    private String payUrl;

    @JsonProperty("qrCodeUrl")
    private String qrCodeUrl;

    @JsonProperty("deepLink")
    private String deepLink;

    @JsonProperty("deeplink")
    private String deeplink;

    @JsonProperty("deeplinkWallet")
    private String deeplinkWallet;

    @JsonProperty("momoOrderId")
    private String momoOrderId;

    @JsonProperty("momoRequestId")
    private String momoRequestId;

    @JsonProperty("zaloPayAppTransId")
    private String zaloPayAppTransId;

    @JsonProperty("zaloPayOrderToken")
    private String zaloPayOrderToken;

    @JsonProperty("timestamp")
    private String timestamp;

    public static PaymentInitiatedEvent fromGatewayResponse(
            String bookingId,
            String customerId,
            String transactionId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String paymentStatus,
            iuh.fit.payment_service.dto.response.GatewayChargeResponse gatewayResponse
    ) {
        String payUrl = gatewayResponse == null ? null : gatewayResponse.getPayUrl();
        String deeplink = gatewayResponse == null ? null : gatewayResponse.getDeeplink();
        return PaymentInitiatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("PaymentInitiated")
                .eventType("PAYMENT_INITIATED")
                .bookingId(bookingId)
                .rideId(bookingId)
                .customerId(customerId)
                .transactionId(transactionId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .paymentUrl(payUrl)
                .payUrl(payUrl)
                .qrCodeUrl(gatewayResponse == null ? null : gatewayResponse.getQrCodeUrl())
                .deepLink(deeplink)
                .deeplink(deeplink)
                .deeplinkWallet(gatewayResponse == null ? null : gatewayResponse.getDeeplinkWallet())
                .momoOrderId(gatewayResponse == null ? null : gatewayResponse.getMomoOrderId())
                .momoRequestId(gatewayResponse == null ? null : gatewayResponse.getMomoRequestId())
                .zaloPayAppTransId(gatewayResponse == null ? null : gatewayResponse.getZaloPayAppTransId())
                .zaloPayOrderToken(gatewayResponse == null ? null : gatewayResponse.getZaloPayOrderToken())
                .timestamp(Instant.now().toString())
                .build();
    }
}
