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
public class MoMoIpnRequest {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("partnerName")
    private String partnerName;

    @JsonProperty("storeId")
    private String storeId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("partnerUserId")
    private String partnerUserId;

    @JsonProperty("resultCode")
    private Integer resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("responseTime")
    private Long responseTime;

    @JsonProperty("transId")
    private Long transId;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("orderInfo")
    private String orderInfo;

    @JsonProperty("orderType")
    private String orderType;

    @JsonProperty("payType")
    private String payType;

    @JsonProperty("extraData")
    private String extraData;
}
