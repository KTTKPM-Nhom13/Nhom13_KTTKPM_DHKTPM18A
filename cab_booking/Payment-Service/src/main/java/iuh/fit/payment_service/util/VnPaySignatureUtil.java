package iuh.fit.payment_service.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * VNPAY Signature Utility - tuân theo tài liệu chính thức VNPAY v2.1.0
 *
 * Quy tắc xây dựng hashData (để tính chữ ký):
 *   fieldName + "=" + URLEncoder.encode(fieldValue, US_ASCII)
 *   (key KHÔNG encode, value encode bằng US_ASCII)
 *
 * Quy tắc xây dựng queryString (URL redirect):
 *   URLEncoder.encode(key, US_ASCII) + "=" + URLEncoder.encode(value, US_ASCII)
 *
 * Ref: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html (Java tab)
 */
@Slf4j
public final class VnPaySignatureUtil {

    private static final String HMAC_SHA512 = "HmacSHA512";

    private VnPaySignatureUtil() {
    }

    /**
     * Tạo chữ ký HMAC-SHA512 cho các tham số gửi đi.
     * Các tham số vnp_SecureHash và vnp_SecureHashType sẽ bị loại trừ.
     */
    public static String sign(Map<String, String> params, String secretKey) {
        String hashData = buildHashData(params, true);
        log.debug("[VNPay] Hash data to sign: {}", hashData);
        return hmacSHA512(secretKey, hashData);
    }

    /**
     * Xác thực chữ ký VNPAY từ callback/IPN/ReturnURL.
     * Các tham số nhận từ HTTP request đã được URL-decode rồi (Spring tự decode),
     * nên cần encode lại theo đúng spec trước khi tính hash.
     */
    public static boolean verify(Map<String, String> params, String receivedHash, String secretKey) {
        if (receivedHash == null || receivedHash.isBlank()) {
            log.warn("[VNPay] Received hash is null or blank");
            return false;
        }

        String computed = sign(params, secretKey);
        boolean valid = computed.equalsIgnoreCase(receivedHash);
        if (!valid) {
            log.warn("[VNPaySignature] Signature mismatch. Expected={}, Got={}", computed, receivedHash);
        }
        return valid;
    }

    /**
     * Xây dựng query string để gắn vào URL redirect.
     * Cả key lẫn value đều encode bằng US_ASCII.
     * vnp_SecureHash và vnp_SecureHashType KHÔNG bị loại trừ (để build full URL).
     */
    public static String buildQueryString(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                query.append(encodeUs(fieldName))
                     .append('=')
                     .append(encodeUs(fieldValue));
                if (hasMoreNonEmpty(fieldNames, fieldName, params, false)) {
                    query.append('&');
                }
            }
        }
        return query.toString();
    }

    /**
     * Xây dựng chuỗi hash data theo đúng spec VNPAY chính thức:
     *   key=URLEncoder.encode(value, US_ASCII)&key2=...
     * Key KHÔNG encode, chỉ value mới encode.
     *
     * @param excludeHashFields nếu true thì bỏ qua vnp_SecureHash và vnp_SecureHashType
     */
    private static String buildHashData(Map<String, String> params, boolean excludeHashFields) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();

            // Bỏ qua các trường hash nếu cần
            if (excludeHashFields
                    && ("vnp_SecureHash".equals(fieldName) || "vnp_SecureHashType".equals(fieldName))) {
                continue;
            }

            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                // Key: RAW (không encode), Value: encode US_ASCII
                hashData.append(fieldName)
                        .append('=')
                        .append(encodeUs(fieldValue));

                // Thêm '&' nếu còn phần tử tiếp theo (không phải phần tử cuối)
                // Kiểm tra bằng cách peek iterator
                if (hasMoreNonEmpty(fieldNames, fieldName, params, excludeHashFields)) {
                    hashData.append('&');
                }
            }
        }
        return hashData.toString();
    }

    /**
     * Kiểm tra xem sau fieldName hiện tại còn phần tử nào hợp lệ không,
     * để quyết định có cần thêm '&' không.
     */
    private static boolean hasMoreNonEmpty(List<String> fieldNames, String currentField,
                                            Map<String, String> params, boolean excludeHashFields) {
        int currentIdx = fieldNames.indexOf(currentField);
        for (int i = currentIdx + 1; i < fieldNames.size(); i++) {
            String name = fieldNames.get(i);
            if (excludeHashFields && ("vnp_SecureHash".equals(name) || "vnp_SecureHashType".equals(name))) {
                continue;
            }
            String val = params.get(name);
            if (val != null && !val.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign VNPay data", e);
        }
    }

    /**
     * URLEncode theo US_ASCII - đây là chuẩn VNPAY yêu cầu cho cả hash data và query string.
     */
    private static String encodeUs(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
