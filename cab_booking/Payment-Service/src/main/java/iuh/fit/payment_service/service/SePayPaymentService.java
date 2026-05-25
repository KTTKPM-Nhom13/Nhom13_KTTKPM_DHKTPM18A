package iuh.fit.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.SePayProperties;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.dto.sepay.SePayWebhookRequest;
import iuh.fit.payment_service.dto.sepay.SePayWebhookResult;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.util.SePaySignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SePayPaymentService {

    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("TXN(?!.*TXN)[A-Za-z0-9]{12}");
    private static final String TRANSFER_IN = "in";

    private final SePayProperties sePayProperties;
    private final ObjectMapper objectMapper;

    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[SePay] Creating VietQR payment - txnId={}, amount={}, bookingId={}",
                request.getTransactionId(), request.getAmount(), request.getBookingId());

        validateQrConfig();

        String qrUrl = buildQrUrl(request);
        return GatewayChargeResponse.builder()
                .success(false)
                .pending(true)
                .gatewayTransactionId(request.getTransactionId())
                .status("PENDING")
                .message("SePay VietQR payment created successfully")
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(PaymentMethod.SEPAY)
                .transactionRef(request.getTransactionId())
                .payUrl(qrUrl)
                .qrCodeUrl(qrUrl)
                .build();
    }

    public SePayWebhookResult processWebhook(
            String rawBody,
            String authorizationHeader,
            String signatureHeader,
            String timestampHeader
    ) {
        verifyApiKey(authorizationHeader);
        verifyHmac(rawBody, signatureHeader, timestampHeader);

        try {
            SePayWebhookRequest request = objectMapper.readValue(rawBody, SePayWebhookRequest.class);
            log.info("[SePay Webhook] Received - id={}, code={}, transferType={}, amount={}, referenceCode={}",
                    request.getId(), request.getCode(), request.getTransferType(),
                    request.getTransferAmount(), request.getReferenceCode());

            if (!TRANSFER_IN.equalsIgnoreCase(valueOrEmpty(request.getTransferType()))) {
                throw new PaymentGatewayException("Ignoring non-inbound SePay transaction id=" + request.getId());
            }

            String transactionId = resolveTransactionId(request);
            if (transactionId == null || transactionId.isBlank()) {
                throw new PaymentGatewayException("Unable to resolve payment transaction from SePay webhook id=" + request.getId());
            }

            return SePayWebhookResult.builder()
                    .success(true)
                    .transactionId(transactionId)
                    .gatewayTransactionId(request.getReferenceCode() != null && !request.getReferenceCode().isBlank()
                            ? request.getReferenceCode()
                            : String.valueOf(request.getId()))
                    .amount(request.getTransferAmount())
                    .message("SePay webhook id=" + request.getId()
                            + ", bank=" + valueOrEmpty(request.getGateway())
                            + ", referenceCode=" + valueOrEmpty(request.getReferenceCode()))
                    .build();
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Invalid SePay webhook payload: " + e.getMessage(), e);
        }
    }

    private String buildQrUrl(GatewayChargeRequest request) {
        BigDecimal amount = request.getAmount().setScale(0, RoundingMode.DOWN);
        String prefix = valueOrEmpty(sePayProperties.getDescriptionPrefix()).trim();
        String txnId = request.getTransactionId();
        String description = txnId;
        if (!prefix.isEmpty() && !txnId.startsWith(prefix)) {
            description = prefix + txnId;
        }

        return UriComponentsBuilder.fromHttpUrl(sePayProperties.getQrBaseUrl())
                .queryParam("acc", sePayProperties.getAccountNumber())
                .queryParam("bank", sePayProperties.getBankCode())
                .queryParam("amount", amount.toPlainString())
                .queryParam("des", description)
                .build()
                .encode()
                .toUriString();
    }

    private void validateQrConfig() {
        if (sePayProperties.getAccountNumber() == null || sePayProperties.getAccountNumber().isBlank()) {
            throw new PaymentGatewayException("Missing SePay account number configuration");
        }
        if (sePayProperties.getBankCode() == null || sePayProperties.getBankCode().isBlank()) {
            throw new PaymentGatewayException("Missing SePay bank code configuration");
        }
    }

    private void verifyApiKey(String authorizationHeader) {
        if (!sePayProperties.isVerifyApiKey()) {
            return;
        }
        String expected = "Apikey " + valueOrEmpty(sePayProperties.getWebhookApiKey());
        if (!constantTimeEquals(expected, valueOrEmpty(authorizationHeader))) {
            throw new PaymentGatewayException("Invalid SePay webhook API key");
        }
    }

    private void verifyHmac(String rawBody, String signatureHeader, String timestampHeader) {
        if (!sePayProperties.isVerifyHmac()) {
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (Exception e) {
            throw new PaymentGatewayException("Invalid SePay webhook timestamp");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > sePayProperties.getTimestampToleranceSeconds()) {
            throw new PaymentGatewayException("Expired SePay webhook timestamp");
        }

        if (!SePaySignatureUtil.verify(timestampHeader, rawBody, signatureHeader, sePayProperties.getWebhookSecret())) {
            throw new PaymentGatewayException("Invalid SePay webhook signature");
        }
    }

    private String resolveTransactionId(SePayWebhookRequest request) {
        String code = request.getCode();
        if (code != null && !code.isBlank()) {
            Matcher codeMatcher = TRANSACTION_ID_PATTERN.matcher(code);
            if (codeMatcher.find()) {
                return codeMatcher.group();
            }
        }

        String content = request.getContent();
        if (content != null && !content.isBlank()) {
            Matcher contentMatcher = TRANSACTION_ID_PATTERN.matcher(content);
            if (contentMatcher.find()) {
                return contentMatcher.group();
            }
        }

        return null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                valueOrEmpty(expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                valueOrEmpty(actual).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
