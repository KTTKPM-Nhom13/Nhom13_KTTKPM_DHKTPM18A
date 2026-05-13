package iuh.fit.payment_service.dto.zalopay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCreateOrderResponse {

    @JsonProperty("return_code")
    private Integer returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    @JsonProperty("sub_return_code")
    private Integer subReturnCode;

    @JsonProperty("sub_return_message")
    private String subReturnMessage;

    @JsonProperty("zp_trans_token")
    private String zpTransToken;

    @JsonProperty("order_url")
    private String orderUrl;

    @JsonProperty("order_token")
    private String orderToken;

    @JsonProperty("qr_code")
    private String qrCode;
}
