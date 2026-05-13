package iuh.fit.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class MoMoChargeRequest {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("accessKey")
    private String accessKey;

    @JsonProperty("partnerName")
    private String partnerName;

    @JsonProperty("storeId")
    private String storeId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderInfo")
    private String orderInfo;

    @JsonProperty("redirectUrl")
    private String redirectUrl;

    @JsonProperty("ipnUrl")
    private String ipnUrl;

    @JsonProperty("lang")
    private String lang;

    @JsonProperty("extraData")
    private String extraData;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("autoCapture")
    private boolean autoCapture = true;

    @JsonProperty("signature")
    private String signature;

    @JsonProperty("startTime")
    private long startTime;
}
