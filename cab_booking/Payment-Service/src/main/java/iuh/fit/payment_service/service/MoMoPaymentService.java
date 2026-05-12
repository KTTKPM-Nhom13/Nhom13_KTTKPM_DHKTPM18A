package iuh.fit.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.MoMoProperties;
import iuh.fit.payment_service.dto.momo.*;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.util.MoMoSignatureUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoMoPaymentService {

    private final MoMoProperties momoProperties;
    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MOMO_SUCCESS_CODE = 0;

    private static final String SIGNATURE_FIELDS_CREATE =
            "partnerCode=%s&accessKey=%s&requestId=%s&amount=%s&orderId=%s&orderInfo=%s&returnUrl=%s&notifyUrl=%s&extraData=%s";

    private static final String SIGNATURE_FIELDS_QUERY =
            "partnerCode=%s&accessKey=%s&requestId=%s&orderId=%s";

    private static final String SIGNATURE_FIELDS_REFUND =
            "partnerCode=%s&accessKey=%s&requestId=%s&amount=%s&orderId=%s&transId=%s&requestType=refundMoMoWallet";

    private static final String SIGNATURE_FIELDS_IPN =
            "partnerCode=%s&accessKey=%s&requestId=%s&amount=%s&orderId=%s&orderInfo=%s&resultCode=%s&extraData=%s";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Retry(name = "paymentGateway")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[MoMo] Creating payment - txnId={}, amount={}, orderId={}",
                request.getTransactionId(), request.getAmount(), request.getTransactionId());

        MoMoChargeRequest momoRequest = buildChargeRequest(request);

        try {
            String payload = objectMapper.writeValueAsString(momoRequest);
            String responseBody = sendPost(momoProperties.getEndpoint() + momoProperties.getCreateUrl(), payload);

            MoMoChargeResponse momoResponse = objectMapper.readValue(responseBody, MoMoChargeResponse.class);
            log.info("[MoMo] Response - resultCode={}, message={}, payUrl={}",
                    momoResponse.getResultCode(), momoResponse.getMessage(), momoResponse.getPayUrl());

            return mapChargeResponse(momoResponse, request);
        } catch (IOException e) {
            log.error("[MoMo] HTTP call failed for txnId={}: {}", request.getTransactionId(), e.getMessage());
            throw new PaymentGatewayException("MoMo API call failed: " + e.getMessage(), e);
        }
    }

    public GatewayChargeResponse chargeFallback(GatewayChargeRequest request, Throwable t) {
        log.error("[MoMo] FALLBACK triggered for txnId={}. Reason: {}",
                request.getTransactionId(), t.getMessage());
        return GatewayChargeResponse.builder()
                .success(false)
                .gatewayTransactionId(null)
                .status("CIRCUIT_OPEN")
                .message("MoMo payment gateway is currently unavailable. Please try again later.")
                .errorCode("CIRCUIT_OPEN")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .build();
    }

    public MoMoIpnResult processIpn(MoMoIpnRequest ipnRequest) {
        log.info("[MoMo IPN] Received - orderId={}, resultCode={}, transId={}, amount={}",
                ipnRequest.getOrderId(), ipnRequest.getResultCode(),
                ipnRequest.getTransId(), ipnRequest.getAmount());

        String extraData = ipnRequest.getExtraData() != null ? ipnRequest.getExtraData() : "";
        String amountStr = String.valueOf(ipnRequest.getAmount());
        String resultCodeStr = String.valueOf(ipnRequest.getResultCode() != null ? ipnRequest.getResultCode() : 0);

        String rawData = String.format(SIGNATURE_FIELDS_IPN,
                ipnRequest.getPartnerCode(),
                momoProperties.getAccessKey(),
                ipnRequest.getRequestId(),
                amountStr,
                ipnRequest.getOrderId(),
                ipnRequest.getOrderInfo(),
                resultCodeStr,
                extraData
        );

        boolean valid = MoMoSignatureUtil.verify(rawData, ipnRequest.getSignature(), momoProperties.getSecretKey());
        if (!valid) {
            log.warn("[MoMo IPN] Invalid signature for orderId={}", ipnRequest.getOrderId());
            return MoMoIpnResult.builder()
                    .success(false)
                    .resultCode(1001)
                    .message("Invalid signature")
                    .build();
        }

        boolean paymentSuccess = ipnRequest.getResultCode() != null && ipnRequest.getResultCode() == MOMO_SUCCESS_CODE;
        return MoMoIpnResult.builder()
                .success(paymentSuccess)
                .resultCode(ipnRequest.getResultCode())
                .message(ipnRequest.getMessage())
                .orderId(ipnRequest.getOrderId())
                .transactionId(ipnRequest.getTransId() != null ? String.valueOf(ipnRequest.getTransId()) : null)
                .amount(BigDecimal.valueOf(ipnRequest.getAmount()))
                .build();
    }

    @Retry(name = "paymentGateway")
    public MoMoQueryResponse queryTransaction(String orderId, String requestId) {
        log.info("[MoMo] Querying transaction - orderId={}, requestId={}", orderId, requestId);

        String rawSignature = String.format(SIGNATURE_FIELDS_QUERY,
                momoProperties.getPartnerCode(),
                momoProperties.getAccessKey(),
                requestId,
                orderId
        );
        String signature = MoMoSignatureUtil.signHmacSHA256(rawSignature, momoProperties.getSecretKey());

        MoMoQueryRequest queryRequest = MoMoQueryRequest.builder()
                .partnerCode(momoProperties.getPartnerCode())
                .accessKey(momoProperties.getAccessKey())
                .requestId(requestId)
                .orderId(orderId)
                .requestType("transactionStatus")
                .signature(signature)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(queryRequest);
            String responseBody = sendPost(momoProperties.getEndpoint() + momoProperties.getQueryUrl(), payload);
            return objectMapper.readValue(responseBody, MoMoQueryResponse.class);
        } catch (IOException e) {
            log.error("[MoMo] Query failed for orderId={}: {}", orderId, e.getMessage());
            throw new RuntimeException("MoMo query failed: " + e.getMessage(), e);
        }
    }

    @Retry(name = "paymentGateway")
    public MoMoRefundResponse refund(Long transId, String orderId, long amount, String requestId, String description) {
        log.info("[MoMo] Refunding - transId={}, orderId={}, amount={}", transId, orderId, amount);

        String amountStr = String.valueOf(amount);
        String transIdStr = String.valueOf(transId);

        String rawSignature = String.format(SIGNATURE_FIELDS_REFUND,
                momoProperties.getPartnerCode(),
                momoProperties.getAccessKey(),
                requestId,
                amountStr,
                orderId,
                transIdStr
        );
        String signature = MoMoSignatureUtil.signHmacSHA256(rawSignature, momoProperties.getSecretKey());

        MoMoRefundRequest refundRequest = MoMoRefundRequest.builder()
                .partnerCode(momoProperties.getPartnerCode())
                .accessKey(momoProperties.getAccessKey())
                .requestId(requestId)
                .transId(transId)
                .amount(amount)
                .orderId(orderId)
                .signature(signature)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(refundRequest);
            String responseBody = sendPost(momoProperties.getEndpoint() + momoProperties.getRefundUrl(), payload);
            return objectMapper.readValue(responseBody, MoMoRefundResponse.class);
        } catch (IOException e) {
            log.error("[MoMo] Refund failed for transId={}: {}", transId, e.getMessage());
            throw new RuntimeException("MoMo refund failed: " + e.getMessage(), e);
        }
    }

    private MoMoChargeRequest buildChargeRequest(GatewayChargeRequest request) {
        String orderId = request.getTransactionId();
        String requestId = request.getTransactionId();
        String orderInfo = "Thanh toan don hang " + orderId;
        String amountStr = String.valueOf(request.getAmount().longValue());

        long startTime = System.currentTimeMillis();

        // Build extraData as base64-encoded JSON (same value used in signature AND body)
        String extraDataBase64 = buildExtraDataBase64(request);

        // Signature format: partnerCode, accessKey, requestId, amount, orderId, orderInfo, returnUrl, notifyUrl, extraData
        String rawSignature = String.format(SIGNATURE_FIELDS_CREATE,
                momoProperties.getPartnerCode(),
                momoProperties.getAccessKey(),
                requestId,
                amountStr,
                orderId,
                orderInfo,
                momoProperties.getReturnUrl(),
                momoProperties.getNotifyUrl(),
                extraDataBase64
        );
        log.info("[MoMo] Raw signature data: {}", rawSignature);
        String signature = MoMoSignatureUtil.signHmacSHA256(rawSignature, momoProperties.getSecretKey());
        log.info("[MoMo] Computed signature: {}", signature);

        return MoMoChargeRequest.builder()
                .partnerCode(momoProperties.getPartnerCode())
                .accessKey(momoProperties.getAccessKey())
                .partnerName(momoProperties.getPartnerName())
                .storeId(momoProperties.getStoreId())
                .requestId(requestId)
                .amount(request.getAmount().longValue())
                .orderId(orderId)
                .orderInfo(orderInfo)
                .redirectUrl(momoProperties.getReturnUrl())
                .ipnUrl(momoProperties.getNotifyUrl())
                .lang(momoProperties.getLang())
                .extraData(extraDataBase64)
                .requestType("captureMoMoWallet")
                .autoCapture(true)
                .signature(signature)
                .startTime(startTime)
                .build();
    }

    private String buildExtraDataBase64(GatewayChargeRequest request) {
        Map<String, String> extraDataMap = new LinkedHashMap<>();
        extraDataMap.put("customerId", request.getCustomerId() != null ? request.getCustomerId() : "");
        extraDataMap.put("bookingId", request.getBookingId() != null ? request.getBookingId() : "");
        extraDataMap.put("transactionId", request.getTransactionId() != null ? request.getTransactionId() : "");

        try {
            String jsonExtra = objectMapper.writeValueAsString(extraDataMap);
            return Base64.getEncoder().encodeToString(jsonExtra.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("[MoMo] Failed to serialize extraData: {}", e.getMessage());
            throw new RuntimeException("Failed to build extraData for MoMo request", e);
        }
    }

    private GatewayChargeResponse mapChargeResponse(MoMoChargeResponse momoResponse, GatewayChargeRequest request) {
        boolean success = momoResponse.getResultCode() != null && momoResponse.getResultCode() == MOMO_SUCCESS_CODE;

        return GatewayChargeResponse.builder()
                .success(success)
                .pending(success)
                .gatewayTransactionId(momoResponse.getTransId() != null ? String.valueOf(momoResponse.getTransId()) : null)
                .status(success ? "PENDING" : "FAILED")
                .message(momoResponse.getMessage())
                .errorCode(success ? null : String.valueOf(momoResponse.getResultCode()))
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .transactionRef(request.getTransactionId())
                .payUrl(momoResponse.getPayUrl())
                .qrCodeUrl(momoResponse.getQrCodeUrl())
                .deeplink(momoResponse.getDeeplink())
                .deeplinkWallet(momoResponse.getDeeplinkWebInApp())
                .momoOrderId(momoResponse.getOrderId())
                .momoRequestId(momoResponse.getRequestId())
                .build();
    }

    private String sendPost(String url, String payload) throws IOException {
        log.debug("[MoMo] POST {} - payload: {}", url, payload);

        RequestBody body = RequestBody.create(payload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            ResponseBody responseBody = response.body();
            String responseStr = responseBody != null ? responseBody.string() : "empty";

            log.debug("[MoMo] Response - status={}, body={}", statusCode, responseStr);

            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP status: " + statusCode + " - body: " + responseStr);
            }
            if (responseBody == null) {
                throw new IOException("Empty response body");
            }
            return responseStr;
        }
    }

}
