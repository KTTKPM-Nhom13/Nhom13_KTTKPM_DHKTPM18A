package iuh.fit.payment_service.dto.zalopay;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZaloPayCallbackRequest {

    private String data;
    private String mac;
    private Integer type;
}
