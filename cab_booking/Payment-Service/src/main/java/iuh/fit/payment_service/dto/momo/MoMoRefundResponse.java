package iuh.fit.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class MoMoRefundResponse {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("transId")
    private Long transId;

    @JsonProperty("resultCode")
    private Integer resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("responseTime")
    private Long responseTime;

    @JsonProperty("refundId")
    private Long refundId;

    @JsonProperty("signature")
    private String signature;
}
