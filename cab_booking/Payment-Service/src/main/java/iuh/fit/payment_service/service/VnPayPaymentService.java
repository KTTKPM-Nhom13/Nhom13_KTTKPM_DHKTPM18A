package iuh.fit.payment_service.service;

import iuh.fit.payment_service.config.VnPayProperties;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.dto.vnpay.VnPayCallbackResult;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.util.VnPaySignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayPaymentService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern VNPAY_SAFE_TEXT = Pattern.compile("[^A-Za-z0-9 ]");
    private static final String SUCCESS_CODE = "00";

    private final VnPayProperties vnPayProperties;

    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[VNPay] Creating payment URL - txnId={}, amount={}, bookingId={}",
                request.getTransactionId(), request.getAmount(), request.getBookingId());

        String vnPayTxnRef = toVnPayTxnRef(request.getTransactionId());
        Map<String, String> params = buildPaymentParams(request, vnPayTxnRef);
        String secureHash = VnPaySignatureUtil.sign(params, vnPayProperties.getHashSecret());

        String payUrl = vnPayProperties.getPaymentUrl()
                + "?" + VnPaySignatureUtil.buildQueryString(params)
                + "&vnp_SecureHash=" + secureHash;
        return GatewayChargeResponse.builder()
                .success(false)
                .pending(true)
                .gatewayTransactionId(vnPayTxnRef)
                .status("PENDING")
                .message("VNPay payment URL created successfully")
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(PaymentMethod.VNPAY)
                .transactionRef(vnPayTxnRef)
                .payUrl(payUrl)
                .build();
    }

    public VnPayCallbackResult processCallback(Map<String, String> callbackParams) {
        String txnRef = callbackParams.get("vnp_TxnRef");
        String responseCode = callbackParams.get("vnp_ResponseCode");
        String transactionStatus = callbackParams.get("vnp_TransactionStatus");
        String secureHash = callbackParams.get("vnp_SecureHash");

        log.info("[VNPay Callback] Received - txnRef={}, responseCode={}, transactionStatus={}",
                txnRef, responseCode, transactionStatus);

        if (!VnPaySignatureUtil.verify(callbackParams, secureHash, vnPayProperties.getHashSecret())) {
            throw new PaymentGatewayException("Invalid VNPay callback signature");
        }
        if (!vnPayProperties.getTmnCode().equals(callbackParams.get("vnp_TmnCode"))) {
            throw new PaymentGatewayException("Invalid VNPay terminal code");
        }

        return VnPayCallbackResult.builder()
                .success(SUCCESS_CODE.equals(responseCode) && SUCCESS_CODE.equals(transactionStatus))
                .transactionId(txnRef)
                .gatewayTransactionId(callbackParams.get("vnp_TransactionNo"))
                .amount(parseAmount(callbackParams.get("vnp_Amount")))
                .responseCode(responseCode)
                .transactionStatus(transactionStatus)
                .message(callbackParams.getOrDefault("vnp_OrderInfo", "VNPay callback processed"))
                .build();
    }

    private Map<String, String> buildPaymentParams(GatewayChargeRequest request, String vnPayTxnRef) {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDateTime expireAt = now.plusMinutes(vnPayProperties.getExpireMinutes());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", vnPayProperties.getCommand());
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", toVnPayAmount(request.getAmount()));
        params.put("vnp_CurrCode", valueOrDefault(request.getCurrency(), vnPayProperties.getCurrCode()));
        params.put("vnp_TxnRef", vnPayTxnRef);
        params.put("vnp_OrderInfo", buildOrderInfo(request));
        params.put("vnp_OrderType", vnPayProperties.getOrderType());
        params.put("vnp_Locale", vnPayProperties.getLocale());
        if (vnPayProperties.getBankCode() != null && !vnPayProperties.getBankCode().isBlank()) {
            params.put("vnp_BankCode", vnPayProperties.getBankCode());
        }
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        params.put("vnp_IpAddr", vnPayProperties.getDefaultIpAddress());
        params.put("vnp_CreateDate", now.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", expireAt.format(VNPAY_DATE_FORMAT));
        return params;
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return null;
        }
        return new BigDecimal(rawAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
    }

    private String buildOrderInfo(GatewayChargeRequest request) {
        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            description = "Payment for booking " + request.getBookingId();
        }
        description = VNPAY_SAFE_TEXT.matcher(description).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return description.length() > 255 ? description.substring(0, 255) : description;
    }

    private String toVnPayTxnRef(String transactionId) {
        return VNPAY_SAFE_TEXT.matcher(transactionId).replaceAll("");
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
