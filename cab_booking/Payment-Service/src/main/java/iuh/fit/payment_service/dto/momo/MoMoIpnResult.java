package iuh.fit.payment_service.dto.momo;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class MoMoIpnResult {

    private boolean success;
    private Integer resultCode;
    private String message;
    private String orderId;
    private String transactionId;
    private BigDecimal amount;
}
