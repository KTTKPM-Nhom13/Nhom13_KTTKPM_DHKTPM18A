package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "momo")
@Getter
@Setter
public class MoMoProperties {

    private String environment = "dev";
    private String endpoint;
    private String createUrl = "/create";
    private String refundUrl = "/refund";
    private String queryUrl = "/query";
    private String confirmUrl = "/confirm";
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String notifyUrl;
    private String returnUrl;
    private String lang = "vi";
    private String partnerName = "CAB Booking";
    private String storeId = "CAB_BOOKING";
}
