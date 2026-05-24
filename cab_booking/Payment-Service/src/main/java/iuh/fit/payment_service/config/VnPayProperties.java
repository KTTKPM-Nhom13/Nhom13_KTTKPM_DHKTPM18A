package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnPayProperties {

    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String tmnCode = "TL1HKBCX";
    private String hashSecret = "A6GA3NSBPW7ZE1P3R1KRZKESNELHRTY3";
    private String returnUrl = "https://scratch-heaving-create.ngrok-free.dev/api/v1/payments/vnpay/return";
    private String ipnUrl = "https://scratch-heaving-create.ngrok-free.dev/api/v1/payments/vnpay/ipn";
    private String mobileReturnUrl = "cabbookingmobile://payment";
    private String version = "2.1.0";
    private String command = "pay";
    private String currCode = "VND";
    private String locale = "vn";
    private String orderType = "other";
    private String bankCode = "NCB";
    private String defaultIpAddress = "127.0.0.1";
    private Integer expireMinutes = 15;
}
