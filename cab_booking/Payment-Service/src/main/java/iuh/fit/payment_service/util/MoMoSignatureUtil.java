package iuh.fit.payment_service.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

@UtilityClass
@Slf4j
public class MoMoSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @SuppressWarnings("resource")
    public static String signHmacSHA256(String data, String secretKey) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHexString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[MoMoSignature] Failed to sign data", e);
            throw new RuntimeException("Failed to sign MoMo request", e);
        }
    }

    @SuppressWarnings("resource")
    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return sb.toString();
    }

    public static boolean verify(String data, String signature, String secretKey) {
        String computed = signHmacSHA256(data, secretKey);
        boolean matches = computed.equalsIgnoreCase(signature);
        if (!matches) {
            log.warn("[MoMoSignature] Signature mismatch. Expected={}, Got={}", computed, signature);
        }
        return matches;
    }
}
