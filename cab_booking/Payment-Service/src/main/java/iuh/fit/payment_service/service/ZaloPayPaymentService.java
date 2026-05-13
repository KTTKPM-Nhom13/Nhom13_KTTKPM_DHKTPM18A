package iuh.fit.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.ZaloPayProperties;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.dto.zalopay.*;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.util.ZaloPaySignatureUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZaloPayPaymentService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int ZALOPAY_CREATE_SUCCESS_CODE = 1;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter APP_TRANS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final ZaloPayProperties zaloPayProperties;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Retry(name = "paymentGateway")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[ZaloPay] Creating payment - txnId={}, amount={}, bookingId={}",
                request.getTransactionId(), request.getAmount(), request.getBookingId());

        try {
            ZaloPayCreateOrderRequest zaloPayRequest = buildCreateOrderRequest(request);
            String payload = objectMapper.writeValueAsString(zaloPayRequest);
            String responseBody = sendPost(zaloPayProperties.getEndpoint() + zaloPayProperties.getCreateUrl(), payload);
            ZaloPayCreateOrderResponse zaloPayResponse =
                    objectMapper.readValue(responseBody, ZaloPayCreateOrderResponse.class);

            log.info("[ZaloPay] Response - returnCode={}, message={}, orderUrl={}",
                    zaloPayResponse.getReturnCode(), zaloPayResponse.getReturnMessage(),
                    zaloPayResponse.getOrderUrl());

            return mapChargeResponse(zaloPayResponse, zaloPayRequest, request);
        } catch (Exception e) {
            log.error("[ZaloPay] Payment creation failed for txnId={}: {}",
                    request.getTransactionId(), e.getMessage());
            throw new PaymentGatewayException("ZaloPay API call failed: " + e.getMessage(), e);
        }
    }

    public GatewayChargeResponse chargeFallback(GatewayChargeRequest request, Throwable t) {
        log.error("[ZaloPay] FALLBACK triggered for txnId={}. Reason={}",
                request.getTransactionId(), t.getMessage());
        return GatewayChargeResponse.builder()
                .success(false)
                .gatewayTransactionId(null)
                .status("CIRCUIT_OPEN")
                .message("ZaloPay payment gateway is currently unavailable. Please try again later.")
                .errorCode("CIRCUIT_OPEN")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .build();
    }

    public ZaloPayCallbackResult processCallback(ZaloPayCallbackRequest callbackRequest) {
        log.info("[ZaloPay Callback] Received - type={}, mac={}",
                callbackRequest.getType(), callbackRequest.getMac());

        if (!ZaloPaySignatureUtil.verify(callbackRequest.getData(),
                callbackRequest.getMac(), zaloPayProperties.getKey2())) {
            throw new PaymentGatewayException("Invalid ZaloPay callback signature");
        }

        try {
            ZaloPayCallbackData callbackData =
                    objectMapper.readValue(callbackRequest.getData(), ZaloPayCallbackData.class);
            String transactionId = extractTransactionId(callbackData);

            return ZaloPayCallbackResult.builder()
                    .success(true)
                    .appTransId(callbackData.getAppTransId())
                    .transactionId(transactionId)
                    .zpTransId(callbackData.getZpTransId() != null ? String.valueOf(callbackData.getZpTransId()) : null)
                    .amount(callbackData.getAmount() != null ? BigDecimal.valueOf(callbackData.getAmount()) : null)
                    .message("ZaloPay callback success")
                    .build();
        } catch (JsonProcessingException e) {
            throw new PaymentGatewayException("Invalid ZaloPay callback data: " + e.getMessage(), e);
        }
    }

    private ZaloPayCreateOrderRequest buildCreateOrderRequest(GatewayChargeRequest request)
            throws JsonProcessingException {
        String appTransId = buildAppTransId(request.getTransactionId());
        String appUser = sanitizeAppUser(request.getCustomerId());
        long appTime = System.currentTimeMillis();
        long amount = request.getAmount().longValue();
        String item = buildItem(request);
        String embedData = buildEmbedData(request);
        String description = buildDescription(request);

        String macInput = zaloPayProperties.getAppId() + "|" + appTransId + "|" + appUser + "|"
                + amount + "|" + appTime + "|" + embedData + "|" + item;
        String mac = ZaloPaySignatureUtil.signHmacSHA256(macInput, zaloPayProperties.getKey1());
        log.debug("[ZaloPay] Create order macInput={}", macInput);

        return ZaloPayCreateOrderRequest.builder()
                .appId(zaloPayProperties.getAppId())
                .appUser(appUser)
                .appTransId(appTransId)
                .appTime(appTime)
                .amount(amount)
                .description(description)
                .callbackUrl(valueOrNull(zaloPayProperties.getCallbackUrl()))
                .expireDurationSeconds(zaloPayProperties.getExpireDurationSeconds())
                .item(item)
                .embedData(embedData)
                .bankCode(zaloPayProperties.getBankCode())
                .mac(mac)
                .build();
    }

    private String buildAppTransId(String transactionId) {
        String datePrefix = LocalDate.now(VIETNAM_ZONE).format(APP_TRANS_DATE_FORMAT);
        String raw = transactionId != null ? transactionId : "txn";
        String normalized = raw.length() > 32 ? raw.substring(0, 32) : raw;
        return datePrefix + "_" + normalized;
    }

    private String sanitizeAppUser(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return "CAB_BOOKING";
        }
        return customerId.length() > 50 ? customerId.substring(0, 50) : customerId;
    }

    private String buildDescription(GatewayChargeRequest request) {
        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            description = "Payment for booking " + request.getBookingId();
        }
        return description.length() > 256 ? description.substring(0, 256) : description;
    }

    private String buildItem(GatewayChargeRequest request) throws JsonProcessingException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemid", request.getBookingId());
        item.put("itemname", "CAB booking " + request.getBookingId());
        item.put("itemprice", request.getAmount().longValue());
        item.put("itemquantity", 1);
        return objectMapper.writeValueAsString(java.util.List.of(item));
    }

    private String buildEmbedData(GatewayChargeRequest request) throws JsonProcessingException {
        Map<String, Object> embedData = new LinkedHashMap<>();
        embedData.put("transactionId", request.getTransactionId());
        embedData.put("bookingId", request.getBookingId());
        embedData.put("customerId", request.getCustomerId());
        if (zaloPayProperties.getRedirectUrl() != null && !zaloPayProperties.getRedirectUrl().isBlank()) {
            embedData.put("redirecturl", zaloPayProperties.getRedirectUrl());
        }
        return objectMapper.writeValueAsString(embedData);
    }

    private String extractTransactionId(ZaloPayCallbackData callbackData) throws JsonProcessingException {
        if (callbackData.getEmbedData() != null && !callbackData.getEmbedData().isBlank()) {
            Map<String, Object> embedData = objectMapper.readValue(
                    callbackData.getEmbedData(), new TypeReference<>() {});
            Object transactionId = embedData.get("transactionId");
            if (transactionId != null && !String.valueOf(transactionId).isBlank()) {
                return String.valueOf(transactionId);
            }
        }

        String appTransId = callbackData.getAppTransId();
        int separatorIndex = appTransId != null ? appTransId.indexOf('_') : -1;
        if (separatorIndex >= 0 && separatorIndex < appTransId.length() - 1) {
            return appTransId.substring(separatorIndex + 1);
        }
        return appTransId;
    }

    private GatewayChargeResponse mapChargeResponse(ZaloPayCreateOrderResponse zaloPayResponse,
                                                    ZaloPayCreateOrderRequest zaloPayRequest,
                                                    GatewayChargeRequest request) {
        boolean created = zaloPayResponse.getReturnCode() != null
                && zaloPayResponse.getReturnCode() == ZALOPAY_CREATE_SUCCESS_CODE;

        return GatewayChargeResponse.builder()
                .success(false)
                .pending(created)
                .gatewayTransactionId(zaloPayResponse.getOrderToken())
                .status(created ? "PENDING" : "FAILED")
                .message(zaloPayResponse.getReturnMessage())
                .errorCode(created ? null : String.valueOf(zaloPayResponse.getReturnCode()))
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .transactionRef(request.getTransactionId())
                .payUrl(zaloPayResponse.getOrderUrl())
                .qrCodeUrl(zaloPayResponse.getQrCode())
                .deeplink(zaloPayResponse.getZpTransToken())
                .zaloPayAppTransId(zaloPayRequest.getAppTransId())
                .zaloPayOrderToken(zaloPayResponse.getOrderToken())
                .build();
    }

    private String sendPost(String url, String payload) throws IOException {
        log.debug("[ZaloPay] POST {} - payload={}", url, payload);

        RequestBody body = RequestBody.create(payload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseText = responseBody != null ? responseBody.string() : "";
            log.debug("[ZaloPay] Response - status={}, body={}", response.code(), responseText);

            if (!response.isSuccessful()) {
                throw new IOException("ZaloPay HTTP " + response.code() + ": " + responseText);
            }

            if (responseText.isBlank()) {
                throw new IOException("ZaloPay returned empty response body");
            }

            return responseText;
        }
    }

    private String valueOrNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
