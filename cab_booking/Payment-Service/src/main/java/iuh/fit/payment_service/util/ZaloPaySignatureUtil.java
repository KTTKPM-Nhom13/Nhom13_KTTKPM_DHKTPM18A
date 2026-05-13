package iuh.fit.payment_service.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
public class ZaloPaySignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private ZaloPaySignatureUtil() {
    }

    public static String signHmacSHA256(String data, String key) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("[ZaloPaySignature] Failed to sign data", e);
            throw new RuntimeException("Failed to sign ZaloPay request", e);
        }
    }

    public static boolean verify(String data, String signature, String key) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String computed = signHmacSHA256(data, key);
        boolean valid = computed.equalsIgnoreCase(signature);
        if (!valid) {
            log.warn("[ZaloPaySignature] Signature mismatch. Expected={}, Got={}", computed, signature);
        }
        return valid;
    }
}
