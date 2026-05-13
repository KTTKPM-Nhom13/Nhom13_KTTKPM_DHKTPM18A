package iuh.fit.payment_service.dto.zalopay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZaloPayCreateOrderRequest {

    @JsonProperty("app_id")
    private Integer appId;

    @JsonProperty("app_user")
    private String appUser;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("app_time")
    private Long appTime;

    private Long amount;
    private String description;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("expire_duration_seconds")
    private Integer expireDurationSeconds;

    private String item;

    @JsonProperty("embed_data")
    private String embedData;

    @JsonProperty("bank_code")
    private String bankCode;

    private String mac;
}
