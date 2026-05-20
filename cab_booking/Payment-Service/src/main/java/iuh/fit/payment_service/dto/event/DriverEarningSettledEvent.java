package iuh.fit.payment_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import iuh.fit.payment_service.entity.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverEarningSettledEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("rideId")
    private String rideId;

    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("driverId")
    private String driverId;

    @JsonProperty("grossAmount")
    private BigDecimal grossAmount;

    @JsonProperty("driverAmount")
    private BigDecimal driverAmount;

    @JsonProperty("platformAmount")
    private BigDecimal platformAmount;

    @JsonProperty("driverSharePercent")
    private BigDecimal driverSharePercent;

    @JsonProperty("platformSharePercent")
    private BigDecimal platformSharePercent;

    @JsonProperty("balanceDelta")
    private BigDecimal balanceDelta;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("paymentStatus")
    private String paymentStatus;

    @JsonProperty("settlementType")
    private String settlementType;

    @JsonProperty("gatewayTransactionId")
    private String gatewayTransactionId;

    @JsonProperty("timestamp")
    private String timestamp;

    public static DriverEarningSettledEvent fromTransaction(
            PaymentTransaction transaction,
            String rideId,
            String driverId,
            BigDecimal driverAmount,
            BigDecimal platformAmount,
            BigDecimal driverSharePercent,
            BigDecimal platformSharePercent,
            BigDecimal balanceDelta,
            String settlementType
    ) {
        String resolvedRideId = hasText(rideId) ? rideId : transaction.getBookingId();
        return DriverEarningSettledEvent.builder()
                .eventId("driver-earning-settled-" + resolvedRideId + "-" + driverId)
                .type("DriverEarningSettled")
                .eventType("DRIVER_EARNING_SETTLED")
                .rideId(resolvedRideId)
                .bookingId(transaction.getBookingId())
                .transactionId(transaction.getTransactionId())
                .customerId(transaction.getCustomerId())
                .driverId(driverId)
                .grossAmount(transaction.getAmount())
                .driverAmount(driverAmount)
                .platformAmount(platformAmount)
                .driverSharePercent(driverSharePercent)
                .platformSharePercent(platformSharePercent)
                .balanceDelta(balanceDelta)
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod().name())
                .paymentStatus(transaction.getStatus().name())
                .settlementType(settlementType)
                .gatewayTransactionId(transaction.getGatewayTransactionId())
                .timestamp(Instant.now().toString())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
