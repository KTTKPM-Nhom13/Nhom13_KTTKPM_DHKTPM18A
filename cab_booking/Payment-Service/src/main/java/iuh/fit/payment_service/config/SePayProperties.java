package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sepay")
@Getter
@Setter
public class SePayProperties {

    private String qrBaseUrl = "https://qr.sepay.vn/img";
    private String accountNumber;
    private String bankCode;
    private String descriptionPrefix = "";
    private String webhookApiKey;
    private String webhookSecret;
    private boolean verifyHmac = false;
    private boolean verifyApiKey = false;
    private long timestampToleranceSeconds = 300;
}
