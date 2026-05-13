package iuh.fit.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mservice.config.Environment;
import com.mservice.processor.QueryTransactionStatus;
import com.mservice.processor.RefundTransaction;
import iuh.fit.payment_service.config.MoMoProperties;
import iuh.fit.payment_service.dto.momo.*;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.exception.ErrorCode;
import iuh.fit.payment_service.exception.PaymentException;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.util.MoMoSignatureUtil;
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
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoMoPaymentService {

    private final MoMoProperties momoProperties;
    private final Environment momoEnvironment;
    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MOMO_SUCCESS_CODE = 0;
    private static final String REQUEST_TYPE_CAPTURE_WALLET = "captureWallet";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Retry(name = "paymentGateway")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[MoMo] Creating payment - txnId={}, amount={}, bookingId={}",
                request.getTransactionId(), request.getAmount(), request.getBookingId());

        try {
            MoMoChargeRequest momoRequest = buildChargeRequest(request);
            String payload = objectMapper.writeValueAsString(momoRequest);
            String responseBody = sendPost(momoProperties.getEndpoint() + momoProperties.getCreateUrl(), payload);
            MoMoChargeResponse momoResponse = objectMapper.readValue(responseBody, MoMoChargeResponse.class);

            log.info("[MoMo] Response - resultCode={}, message={}, payUrl={}",
                    momoResponse.getResultCode(), momoResponse.getMessage(), momoResponse.getPayUrl());

            return mapChargeResponse(momoResponse, request);
        } catch (Exception e) {
            log.error("[MoMo] Payment creation failed for txnId={}: {}", request.getTransactionId(), e.getMessage());
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

        validatePartnerCode(ipnRequest);
        verifyIpnSignature(ipnRequest);

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

    private void validatePartnerCode(MoMoIpnRequest ipnRequest) {
        if (!valueOrEmpty(momoProperties.getPartnerCode()).equals(ipnRequest.getPartnerCode())) {
            log.error("[MoMo IPN] Partner code mismatch for orderId={}, expected={}, actual={}",
                    ipnRequest.getOrderId(), momoProperties.getPartnerCode(), ipnRequest.getPartnerCode());
            throw new PaymentException(ErrorCode.MOMO_SIGNATURE_INVALID,
                    "Invalid MoMo partnerCode for orderId: " + ipnRequest.getOrderId());
        }
    }

    private void verifyIpnSignature(MoMoIpnRequest ipnRequest) {
        String rawData = buildIpnRawData(ipnRequest);
        boolean validSignature = MoMoSignatureUtil.verify(rawData, ipnRequest.getSignature(), momoProperties.getSecretKey());
        if (!validSignature) {
            log.error("[MoMo IPN] Invalid signature for orderId={}", ipnRequest.getOrderId());
            throw new PaymentException(ErrorCode.MOMO_SIGNATURE_INVALID,
                    "Invalid MoMo IPN signature for orderId: " + ipnRequest.getOrderId());
        }
    }

    private String buildIpnRawData(MoMoIpnRequest ipnRequest) {
        return "accessKey=" + valueOrEmpty(momoProperties.getAccessKey()) +
                "&amount=" + valueOrEmpty(ipnRequest.getAmount()) +
                "&extraData=" + valueOrEmpty(ipnRequest.getExtraData()) +
                "&message=" + valueOrEmpty(ipnRequest.getMessage()) +
                "&orderId=" + valueOrEmpty(ipnRequest.getOrderId()) +
                "&orderInfo=" + valueOrEmpty(ipnRequest.getOrderInfo()) +
                "&orderType=" + valueOrEmpty(ipnRequest.getOrderType()) +
                "&partnerCode=" + valueOrEmpty(ipnRequest.getPartnerCode()) +
                "&payType=" + valueOrEmpty(ipnRequest.getPayType()) +
                "&requestId=" + valueOrEmpty(ipnRequest.getRequestId()) +
                "&responseTime=" + valueOrEmpty(ipnRequest.getResponseTime()) +
                "&resultCode=" + valueOrEmpty(ipnRequest.getResultCode()) +
                "&transId=" + valueOrEmpty(ipnRequest.getTransId());
    }

    private String valueOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @Retry(name = "paymentGateway")
    public MoMoQueryResponse queryTransaction(String orderId, String requestId) {
        log.info("[MoMo] Querying transaction - orderId={}, requestId={}", orderId, requestId);

        try {
            com.mservice.models.QueryStatusTransactionResponse momoResponse = 
                    QueryTransactionStatus.process(momoEnvironment, orderId, requestId);

            return MoMoQueryResponse.builder()
                    .partnerCode(momoResponse.getPartnerCode())
                    .orderId(momoResponse.getOrderId())
                    .requestId(momoResponse.getRequestId())
                    .resultCode(momoResponse.getResultCode())
                    .message(momoResponse.getMessage())
                    .responseTime(momoResponse.getResponseTime())
                    .build();
        } catch (Exception e) {
            log.error("[MoMo] Query failed for orderId={}: {}", orderId, e.getMessage());
            throw new RuntimeException("MoMo query failed: " + e.getMessage(), e);
        }
    }

    @Retry(name = "paymentGateway")
    public MoMoRefundResponse refund(Long transId, String orderId, long amount, String requestId, String description) {
        log.info("[MoMo] Refunding - transId={}, orderId={}, amount={}", transId, orderId, amount);

        try {
            com.mservice.models.RefundMoMoResponse momoResponse = RefundTransaction.process(
                    momoEnvironment,
                    orderId,
                    requestId,
                    String.valueOf(amount),
                    transId,
                    description != null ? description : ""
            );

            return MoMoRefundResponse.builder()
                    .partnerCode(momoResponse.getPartnerCode())
                    .orderId(momoResponse.getOrderId())
                    .requestId(momoResponse.getRequestId())
                    .resultCode(momoResponse.getResultCode())
                    .message(momoResponse.getMessage())
                    .responseTime(momoResponse.getResponseTime())
                    .refundId(momoResponse.getRefundId())
                    .transId(momoResponse.getTransId())
                    .build();
        } catch (Exception e) {
            log.error("[MoMo] Refund failed for transId={}: {}", transId, e.getMessage());
            throw new RuntimeException("MoMo refund failed: " + e.getMessage(), e);
        }
    }

    private MoMoChargeRequest buildChargeRequest(GatewayChargeRequest request) {
        String orderId = request.getTransactionId();
        String requestId = request.getTransactionId();
        String amount = String.valueOf(request.getAmount().longValue());
        String orderInfo = "Pay_for_booking_" + request.getBookingId();
        String redirectUrl = valueOrEmpty(momoProperties.getReturnUrl());
        String ipnUrl = valueOrEmpty(momoProperties.getNotifyUrl());
        String extraData = buildExtraDataBase64(request);

        String rawData = "accessKey=" + valueOrEmpty(momoProperties.getAccessKey()) +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + valueOrEmpty(momoProperties.getPartnerCode()) +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + REQUEST_TYPE_CAPTURE_WALLET;

        String signature = MoMoSignatureUtil.signHmacSHA256(rawData, momoProperties.getSecretKey());
        log.debug("[MoMo] Create payment rawData={}", rawData);

        return MoMoChargeRequest.builder()
                .partnerCode(momoProperties.getPartnerCode())
                .partnerName(momoProperties.getPartnerName())
                .storeId(momoProperties.getStoreId())
                .requestId(requestId)
                .amount(request.getAmount().longValue())
                .orderId(orderId)
                .orderInfo(orderInfo)
                .redirectUrl(redirectUrl)
                .ipnUrl(ipnUrl)
                .lang(momoProperties.getLang())
                .extraData(extraData)
                .requestType(REQUEST_TYPE_CAPTURE_WALLET)
                .autoCapture(true)
                .signature(signature)
                .startTime(System.currentTimeMillis())
                .build();
    }

    private String buildExtraDataBase64(GatewayChargeRequest request) {
        try {
            java.util.LinkedHashMap<String, String> extraDataMap = new java.util.LinkedHashMap<>();
            extraDataMap.put("customerId", request.getCustomerId() != null ? request.getCustomerId() : "");
            extraDataMap.put("bookingId", request.getBookingId() != null ? request.getBookingId() : "");
            extraDataMap.put("transactionId", request.getTransactionId() != null ? request.getTransactionId() : "");

            String jsonExtra = objectMapper.writeValueAsString(extraDataMap);
            return java.util.Base64.getEncoder().encodeToString(jsonExtra.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("[MoMo] Failed to serialize extraData: {}", e.getMessage());
            return "";
        }
    }

    private GatewayChargeResponse mapChargeResponse(MoMoChargeResponse momoResponse, GatewayChargeRequest request) {
        boolean created = momoResponse.getResultCode() != null && momoResponse.getResultCode() == MOMO_SUCCESS_CODE;

        return GatewayChargeResponse.builder()
                .success(false)
                .pending(created)
                .gatewayTransactionId(momoResponse.getTransId() != null ? String.valueOf(momoResponse.getTransId()) : null)
                .status(created ? "PENDING" : "FAILED")
                .message(momoResponse.getMessage())
                .errorCode(created ? null : String.valueOf(momoResponse.getResultCode()))
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
        log.debug("[MoMo] POST {} - payload={}", url, payload);

        RequestBody body = RequestBody.create(payload, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseText = responseBody != null ? responseBody.string() : "";
            log.debug("[MoMo] Response - status={}, body={}", response.code(), responseText);

            if (!response.isSuccessful()) {
                throw new IOException("MoMo HTTP " + response.code() + ": " + responseText);
            }

            if (responseText.isBlank()) {
                throw new IOException("MoMo returned empty response body");
            }

            return responseText;
        }
    }
}
