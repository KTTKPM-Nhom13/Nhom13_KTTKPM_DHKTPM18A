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
public class MoMoChargeResponse {

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

    @JsonProperty("payUrl")
    private String payUrl;

    @JsonProperty("shortLink")
    private String shortLink;

    @JsonProperty("deeplink")
    private String deeplink;

    @JsonProperty("qrCodeUrl")
    private String qrCodeUrl;

    @JsonProperty("deeplinkWebInApp")
    private String deeplinkWebInApp;

    @JsonProperty("applink")
    private String applink;

    @JsonProperty("partnerClientId")
    private String partnerClientId;

    @JsonProperty("bindingUrl")
    private String bindingUrl;

    @JsonProperty("deeplinkMiniApp")
    private String deeplinkMiniApp;
}
