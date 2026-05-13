package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoMoPropertiesExtended {
    private String endpoint;
    private String createUrl = "/create";
    private String refundUrl = "/refund";
    private String queryUrl = "/query";
    private String confirmUrl = "/confirm";
    private String tokenPayUrl = "/tokenization/pay";
    private String tokenBindUrl = "/tokenization/bind";
    private String tokenInquiryUrl = "/tokenization/cbQuery";
    private String tokenDeleteUrl = "/tokenization/delete";
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String notifyUrl;
    private String returnUrl;
    private String lang = "vi";
    private String partnerName = "CAB Booking";
    private String storeId = "CAB_BOOKING";
}
