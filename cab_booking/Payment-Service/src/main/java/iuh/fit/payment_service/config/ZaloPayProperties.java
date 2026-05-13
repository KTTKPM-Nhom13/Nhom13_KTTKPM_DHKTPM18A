package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "zalopay")
@Getter
@Setter
public class ZaloPayProperties {

    private String endpoint = "https://sb-openapi.zalopay.vn/v2";
    private String createUrl = "/create";
    private Integer appId = 2553;
    private String key1 = "PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL";
    private String key2 = "kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz";
    private String callbackUrl;
    private String redirectUrl;
    private String bankCode = "";
    private Integer expireDurationSeconds = 900;
}
