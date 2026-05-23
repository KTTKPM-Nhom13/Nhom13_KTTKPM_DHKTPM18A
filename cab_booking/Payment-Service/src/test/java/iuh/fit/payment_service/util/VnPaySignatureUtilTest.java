package iuh.fit.payment_service.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPaySignatureUtilTest {

    private static final String SECRET = "A6GA3NSBPW7ZE1P3R1KRZKESNELHRTY3";

    @Test
    void signUsesEncodedHashDataExpectedByVnPay() {
        Map<String, String> params = paymentParams();

        assertThat(VnPaySignatureUtil.sign(params, SECRET))
                .isEqualTo("1b9dd8bc0c6368d4eb056d688d2f87fe8eae035fcc3b1722928927ab4d5c0be8116672203c1d160297470444d39d86d2e6584a3c823b0ecf34c0f0dc351dc8ed");
    }

    @Test
    void verifyIgnoresSecureHashFieldsAndUsesDecodedCallbackValues() {
        Map<String, String> params = paymentParams();
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("vnp_SecureHash", "will-be-ignored");

        assertThat(VnPaySignatureUtil.verify(
                params,
                "1b9dd8bc0c6368d4eb056d688d2f87fe8eae035fcc3b1722928927ab4d5c0be8116672203c1d160297470444d39d86d2e6584a3c823b0ecf34c0f0dc351dc8ed",
                SECRET))
                .isTrue();
    }

    private Map<String, String> paymentParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "5000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_Command", "pay");
        params.put("vnp_CreateDate", "20260519153013");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_ExpireDate", "20260519154513");
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", "Payment for booking B VNPAY 1779179413330");
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", "https://scratch-heaving-create.ngrok-free.dev/api/v1/payments/vnpay/return");
        params.put("vnp_TmnCode", "TL1HKBCX");
        params.put("vnp_TxnRef", "TXN8C90F495CE75");
        params.put("vnp_Version", "2.1.0");
        return params;
    }
}
