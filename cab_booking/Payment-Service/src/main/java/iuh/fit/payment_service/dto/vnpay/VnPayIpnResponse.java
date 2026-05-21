package iuh.fit.payment_service.dto.vnpay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VnPayIpnResponse {

    @JsonProperty("RspCode")
    private String rspCode;

    @JsonProperty("Message")
    private String message;

    public static VnPayIpnResponse success() {
        return new VnPayIpnResponse("00", "Confirm Success");
    }

    public static VnPayIpnResponse invalid(String message) {
        return new VnPayIpnResponse("97", message);
    }
}
