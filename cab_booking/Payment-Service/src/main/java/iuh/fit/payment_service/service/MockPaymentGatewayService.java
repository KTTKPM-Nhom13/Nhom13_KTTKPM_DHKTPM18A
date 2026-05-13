package iuh.fit.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import iuh.fit.payment_service.dto.request.ChargePaymentRequest;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGatewayService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    private static final double SUCCESS_RATE = 0.9;

    @Retry(name = "paymentGateway")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        log.info("[MockGateway] Charging - txnId={}, amount={}, method={}, customer={}",
                request.getTransactionId(), request.getAmount(), request.getPaymentMethod(), request.getCustomerId());

        simulateNetworkDelay();

        if (shouldSimulateFailure()) {
            return simulateFailure(request);
        }

        return simulateSuccess(request);
    }

    public GatewayChargeResponse chargeFallback(GatewayChargeRequest request, Throwable t) {
        log.error("[MockGateway] FALLBACK triggered for txnId={}. Reason: {}",
                request.getTransactionId(), t.getMessage());
        return GatewayChargeResponse.builder()
                .success(false)
                .gatewayTransactionId(null)
                .status("CIRCUIT_OPEN")
                .message("Payment gateway is currently unavailable. Please try again later.")
                .errorCode("CIRCUIT_OPEN")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .build();
    }

    public GatewayChargeResponse refund(String gatewayTransactionId, BigDecimal amount) {
        log.info("[MockGateway] Refunding - gatewayTxnId={}, amount={}", gatewayTransactionId, amount);
        simulateNetworkDelay();
        return GatewayChargeResponse.builder()
                .success(true)
                .gatewayTransactionId("refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .status("REFUNDED")
                .message("Refund processed successfully")
                .amount(amount)
                .build();
    }

    public GatewayChargeResponse getTransactionStatus(String gatewayTransactionId) {
        log.info("[MockGateway] Querying status - gatewayTxnId={}", gatewayTransactionId);
        return GatewayChargeResponse.builder()
                .success(true)
                .gatewayTransactionId(gatewayTransactionId)
                .status("SETTLED")
                .message("Transaction settled")
                .build();
    }

    private GatewayChargeResponse simulateSuccess(GatewayChargeRequest request) {
        String gatewayTxnId = "gw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return GatewayChargeResponse.builder()
                .success(true)
                .gatewayTransactionId(gatewayTxnId)
                .status("CAPTURED")
                .message("Payment captured successfully")
                .errorCode(null)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .transactionRef(gatewayTxnId)
                .build();
    }

    private GatewayChargeResponse simulateFailure(GatewayChargeRequest request) {
        String[] errorCodes = {"INSUFFICIENT_FUNDS", "CARD_DECLINED", "INVALID_CARD", "EXPIRED_CARD", "NETWORK_ERROR"};
        String errorCode = errorCodes[random.nextInt(errorCodes.length)];
        String[] messages = {
                "Insufficient funds in account",
                "Card was declined by issuing bank",
                "Invalid card number or CVV",
                "Card has expired",
                "Network timeout, please retry"
        };
        int idx = errorCode.equals("NETWORK_ERROR") ? 4 :
                errorCode.equals("INSUFFICIENT_FUNDS") ? 0 :
                        errorCode.equals("CARD_DECLINED") ? 1 :
                                errorCode.equals("INVALID_CARD") ? 2 : 3;

        return GatewayChargeResponse.builder()
                .success(false)
                .gatewayTransactionId(null)
                .status("FAILED")
                .message(messages[idx])
                .errorCode(errorCode)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .build();
    }

    private boolean shouldSimulateFailure() {
        return random.nextDouble() > SUCCESS_RATE;
    }

    private void simulateNetworkDelay() {
        try {
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
