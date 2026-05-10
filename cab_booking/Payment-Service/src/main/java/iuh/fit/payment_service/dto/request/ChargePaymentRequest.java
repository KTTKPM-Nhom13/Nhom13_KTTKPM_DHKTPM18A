package iuh.fit.payment_service.dto.request;

import iuh.fit.payment_service.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargePaymentRequest {

    @NotBlank(message = "Ride ID is required")
    private String rideId;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum amount is 1000 VND")
    @DecimalMax(value = "100000000.00", message = "Maximum amount exceeded")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String currency;

    private String description;

    @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
    private String idempotencyKey;
}
