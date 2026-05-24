package iuh.fit.payment_service.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SePaySignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private SePaySignatureUtil() {
    }

    public static String sign(String timestamp, String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_PREFIX + toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign SePay webhook payload", e);
        }
    }

    public static boolean verify(String timestamp, String rawBody, String signature, String secret) {
        if (timestamp == null || rawBody == null || signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(timestamp, rawBody, secret).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
