package iuh.fit.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class MoMoQueryRequest {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("accessKey")
    private String accessKey;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("requestType")
    private String requestType;

    @JsonProperty("signature")
    private String signature;
}
