package iuh.fit.payment_service.dto.zalopay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCallbackResponse {

    @JsonProperty("return_code")
    private int returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    public static ZaloPayCallbackResponse success() {
        return ZaloPayCallbackResponse.builder()
                .returnCode(1)
                .returnMessage("Success")
                .build();
    }

    public static ZaloPayCallbackResponse invalid(String message) {
        return ZaloPayCallbackResponse.builder()
                .returnCode(2)
                .returnMessage(message)
                .build();
    }
}
