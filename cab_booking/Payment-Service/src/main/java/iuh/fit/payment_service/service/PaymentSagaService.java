package iuh.fit.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.RedisConfig.IdempotencyRedisService;
import iuh.fit.payment_service.dto.event.PaymentCompletedEvent;
import iuh.fit.payment_service.dto.event.PaymentFailedEvent;
import iuh.fit.payment_service.dto.momo.MoMoIpnResult;
import iuh.fit.payment_service.dto.request.ChargePaymentRequest;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.dto.response.PaymentResponse;
import iuh.fit.payment_service.dto.zalopay.ZaloPayCallbackResult;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.exception.ErrorCode;
import iuh.fit.payment_service.exception.IdempotencyConflictException;
import iuh.fit.payment_service.exception.PaymentException;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaService {

    private final PaymentTransactionRepository paymentRepository;
    private final IdempotencyRedisService idempotencyRedisService;
    private final MockPaymentGatewayService gatewayService;
    private final MoMoPaymentService moMoPaymentService;
    private final ZaloPayPaymentService zaloPayPaymentService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY = 3;

    public PaymentResponse startPaymentSaga(ChargePaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        log.info("[Saga] Starting payment saga - bookingId={}, customerId={}, amount={}, method={}, idempotencyKey={}",
                request.getBookingId(), request.getCustomerId(), request.getAmount(), request.getPaymentMethod(), idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return handleIdempotency(idempotencyKey, request);
        }

        PaymentTransaction transaction = createAndSaveTransaction(request);
        return executePayment(transaction);
    }

    @Transactional
    public PaymentResponse handleIdempotency(String idempotencyKey, ChargePaymentRequest request) {
        String cachedTxnId = idempotencyRedisService.get(idempotencyKey);

        if (cachedTxnId != null) {
            log.info("[Saga] Idempotency HIT in Redis - key={}, txnId={}", idempotencyKey, cachedTxnId);
            return paymentRepository.findByTransactionId(cachedTxnId)
                    .map(PaymentResponse::fromEntity)
                    .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                            "Cached transaction not found: " + cachedTxnId));
        }

        if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("[Saga] Idempotency HIT in DB - key={}", idempotencyKey);
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(PaymentResponse::fromEntity)
                    .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        }

        if (!idempotencyRedisService.setIfAbsent(idempotencyKey, "PENDING")) {
            log.warn("[Saga] Idempotency key race condition - key={}", idempotencyKey);
            throw new IdempotencyConflictException(idempotencyKey, "PENDING");
        }

        try {
            PaymentTransaction transaction = createAndSaveTransaction(request);
            idempotencyRedisService.put(idempotencyKey, transaction.getTransactionId());
            return executePayment(transaction);
        } catch (Exception e) {
            idempotencyRedisService.delete(idempotencyKey);
            throw e;
        }
    }

    @Transactional
    public PaymentTransaction createAndSaveTransaction(ChargePaymentRequest request) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId("txn_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .bookingId(request.getBookingId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .retryCount(0)
                .paymentGatewayName(resolveGatewayName(request.getPaymentMethod()))
                .build();

        transaction = paymentRepository.save(transaction);
        log.info("[Saga] Transaction created - txnId={}, status=PENDING", transaction.getTransactionId());
        return transaction;
    }

    public PaymentResponse executePayment(PaymentTransaction transaction) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY) {
            attempt++;
            updateRetryCount(transaction, attempt - 1);
            log.info("[Saga] Payment attempt {}/{} - txnId={}", attempt, MAX_RETRY, transaction.getTransactionId());

            try {
                GatewayChargeResponse gatewayResponse = callPaymentGateway(transaction);

                if (gatewayResponse.isSuccess()) {
                    markAndPublishSuccess(transaction, gatewayResponse);
                    return PaymentResponse.fromEntity(transaction, "Payment completed successfully");
                }

                if (gatewayResponse.isPending()) {
                    log.info("[Saga] {} payment PENDING - txnId={}, awaiting callback",
                            transaction.getPaymentMethod(),
                            transaction.getTransactionId());
                    updateStatusWithGatewayResponse(transaction, PaymentStatus.PENDING, gatewayResponse);
                    PaymentResponse resp = PaymentResponse.fromEntity(transaction,
                            transaction.getPaymentMethod() + " payment initiated, awaiting customer confirmation");
                    resp.setPayUrl(gatewayResponse.getPayUrl());
                    resp.setQrCodeUrl(gatewayResponse.getQrCodeUrl());
                    resp.setDeeplink(gatewayResponse.getDeeplink());
                    resp.setDeeplinkWallet(gatewayResponse.getDeeplinkWallet());
                    resp.setMomoOrderId(gatewayResponse.getMomoOrderId());
                    resp.setMomoRequestId(gatewayResponse.getMomoRequestId());
                    resp.setZaloPayAppTransId(gatewayResponse.getZaloPayAppTransId());
                    resp.setZaloPayOrderToken(gatewayResponse.getZaloPayOrderToken());
                    return resp;
                }

                String reason = gatewayResponse.getErrorCode() + ": " + gatewayResponse.getMessage();
                lastException = new PaymentGatewayException(gatewayResponse.getErrorCode(), reason);
                log.warn("[Saga] Gateway returned failure - txnId={}, error={}",
                        transaction.getTransactionId(), reason);

                if (attempt >= MAX_RETRY) {
                    markAndPublishFailed(transaction, reason, false);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + reason);
                }

                markForRetry(transaction, reason);
                waitBeforeRetry(attempt);

            } catch (PaymentGatewayException e) {
                lastException = e;
                log.error("[Saga] Gateway exception on attempt {} - txnId={}, error={}",
                        attempt, transaction.getTransactionId(), e.getMessage());

                if (attempt >= MAX_RETRY) {
                    markAndPublishFailed(transaction, e.getGatewayMessage(), false);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + e.getGatewayMessage());
                }

                markForRetry(transaction, e.getGatewayMessage());
                waitBeforeRetry(attempt);

            } catch (Exception e) {
                lastException = e;
                log.error("[Saga] Unexpected exception on attempt {} - txnId={}", attempt, transaction.getTransactionId(), e);

                if (attempt >= MAX_RETRY) {
                    markAndPublishFailed(transaction, "Unexpected error: " + e.getMessage(), false);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + e.getMessage());
                }

                markForRetry(transaction, "Unexpected error: " + e.getMessage());
                waitBeforeRetry(attempt);
            }
        }

        markAndPublishFailed(transaction, "Payment failed after max retries", false);
        return PaymentResponse.fromEntity(transaction, "Payment failed after max retries");
    }

    @Transactional
    public void updateRetryCount(PaymentTransaction transaction, int count) {
        transaction.setRetryCount(count);
        paymentRepository.save(transaction);
    }

    @Transactional
    public void updateStatus(PaymentTransaction transaction, PaymentStatus status) {
        transaction.setStatus(status);
        paymentRepository.save(transaction);
    }

    @Transactional
    public void updateStatusWithGatewayResponse(PaymentTransaction transaction, PaymentStatus status,
                                                 GatewayChargeResponse gatewayResponse) {
        transaction.setStatus(status);
        try {
            transaction.setGatewayResponseJson(objectMapper.writeValueAsString(gatewayResponse));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[Saga] Failed to serialize gateway response for PENDING txnId={}: {}",
                    transaction.getTransactionId(), e.getMessage());
        }
        paymentRepository.save(transaction);
    }

    @Transactional
    public void markAndPublishSuccess(PaymentTransaction transaction, GatewayChargeResponse gatewayResponse) {
        try {
            transaction.markSuccess(gatewayResponse.getGatewayTransactionId(),
                    objectMapper.writeValueAsString(gatewayResponse));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[Saga] Failed to serialize gateway response for txnId={}: {}",
                    transaction.getTransactionId(), e.getMessage());
            transaction.markSuccess(gatewayResponse.getGatewayTransactionId(), null);
        }
        paymentRepository.save(transaction);
        log.info("[Saga] Payment SUCCESS - txnId={}, gatewayTxnId={}",
                transaction.getTransactionId(), gatewayResponse.getGatewayTransactionId());
        outboxService.saveOutboxEventInTx(
                "PaymentTransaction",
                transaction.getTransactionId(),
                "PAYMENT_COMPLETED",
                PaymentCompletedEvent.fromTransaction(
                        transaction.getBookingId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getGatewayTransactionId(),
                        transaction.getPaymentMethod().name()
                )
        );
    }

    @Transactional
    public void markAndPublishFailed(PaymentTransaction transaction, String reason, boolean isRetry) {
        transaction.markFailed(reason, isRetry);
        paymentRepository.save(transaction);
        log.error("[Saga] Payment FAILED - txnId={}, reason={}, isRetry={}",
                transaction.getTransactionId(), reason, isRetry);
        outboxService.saveOutboxEventInTx(
                "PaymentTransaction",
                transaction.getTransactionId(),
                "PAYMENT_FAILED",
                PaymentFailedEvent.fromTransaction(
                        transaction.getBookingId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        reason,
                        transaction.getRetryCount()
                )
        );
    }

    @Transactional
    public void markForRetry(PaymentTransaction transaction, String reason) {
        transaction.markFailed(reason, true);
        paymentRepository.save(transaction);
        log.warn("[Saga] Payment failed, scheduling retry - txnId={}, retryCount={}",
                transaction.getTransactionId(), transaction.getRetryCount());
    }

    private GatewayChargeResponse callPaymentGateway(PaymentTransaction transaction) {
        GatewayChargeRequest gatewayRequest = GatewayChargeRequest.builder()
                .transactionId(transaction.getTransactionId())
                .customerId(transaction.getCustomerId())
                .bookingId(transaction.getBookingId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .description("Payment for booking " + transaction.getBookingId())
                .build();

        if (transaction.getPaymentMethod() == PaymentMethod.MOMO) {
            return moMoPaymentService.charge(gatewayRequest);
        }
        if (transaction.getPaymentMethod() == PaymentMethod.ZALOPAY) {
            return zaloPayPaymentService.charge(gatewayRequest);
        }
        return gatewayService.charge(gatewayRequest);
    }

    private String resolveGatewayName(PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.MOMO) {
            return "MOMO";
        }
        if (paymentMethod == PaymentMethod.ZALOPAY) {
            return "ZALOPAY";
        }
        return "MOCK_GATEWAY";
    }

    private void waitBeforeRetry(int attempt) {
        try {
            long delayMs = (long) Math.pow(2, attempt) * 1000L;
            log.info("[Saga] Waiting {}ms before retry...", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Saga] Retry wait interrupted");
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        log.debug("[Saga] Fetching payment - txnId={}", transactionId);
        PaymentTransaction transaction = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Transaction not found: " + transactionId));
        return PaymentResponse.fromEntity(transaction);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(String bookingId) {
        log.debug("[Saga] Fetching payment by bookingId={}", bookingId);
        PaymentTransaction transaction = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found for booking: " + bookingId));
        return PaymentResponse.fromEntity(transaction);
    }

    @Transactional
    public void completePaymentFromMoMoIpn(MoMoIpnResult ipnResult) {
        log.info("[Saga] Processing MoMo IPN completion - orderId={}, transId={}, success={}",
                ipnResult.getOrderId(), ipnResult.getTransactionId(), ipnResult.isSuccess());

        PaymentTransaction transaction = paymentRepository.findByTransactionId(ipnResult.getOrderId())
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Transaction not found: " + ipnResult.getOrderId()));

        if (transaction.getStatus() != PaymentStatus.PENDING) {
            log.info("[Saga] Ignoring MoMo IPN for non-pending transaction - txnId={}, status={}",
                    transaction.getTransactionId(), transaction.getStatus());
            return;
        }

        if (ipnResult.getAmount() == null || transaction.getAmount().compareTo(ipnResult.getAmount()) != 0) {
            log.error("[Saga] MoMo IPN amount mismatch - txnId={}, expected={}, actual={}",
                    transaction.getTransactionId(), transaction.getAmount(), ipnResult.getAmount());
            throw new PaymentException(ErrorCode.VALIDATION_ERROR,
                    "MoMo IPN amount mismatch for transaction: " + transaction.getTransactionId());
        }

        if (ipnResult.isSuccess()) {
            transaction.markSuccess(
                    ipnResult.getTransactionId(),
                    "MoMo IPN: resultCode=" + ipnResult.getResultCode() + ", message=" + ipnResult.getMessage()
            );
            paymentRepository.save(transaction);
            log.info("[Saga] MoMo payment SUCCESS from IPN - txnId={}, gatewayTxnId={}",
                    transaction.getTransactionId(), ipnResult.getTransactionId());
            outboxService.saveOutboxEventInTx(
                    "PaymentTransaction",
                    transaction.getTransactionId(),
                    "PAYMENT_COMPLETED",
                    PaymentCompletedEvent.fromTransaction(
                            transaction.getBookingId(),
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            transaction.getGatewayTransactionId(),
                            transaction.getPaymentMethod().name()
                    )
            );
        } else {
            String reason = "MoMo resultCode=" + ipnResult.getResultCode() + ": " + ipnResult.getMessage();
            transaction.markFailed(reason, false);
            paymentRepository.save(transaction);
            log.warn("[Saga] MoMo payment FAILED from IPN - txnId={}, reason={}",
                    transaction.getTransactionId(), reason);
            outboxService.saveOutboxEventInTx(
                    "PaymentTransaction",
                    transaction.getTransactionId(),
                    "PAYMENT_FAILED",
                    PaymentFailedEvent.fromTransaction(
                            transaction.getBookingId(),
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            reason,
                            transaction.getRetryCount()
                    )
            );
        }
    }

    @Transactional
    public void completePaymentFromZaloPayCallback(ZaloPayCallbackResult callbackResult) {
        log.info("[Saga] Processing ZaloPay callback completion - appTransId={}, txnId={}, zpTransId={}",
                callbackResult.getAppTransId(), callbackResult.getTransactionId(), callbackResult.getZpTransId());

        PaymentTransaction transaction = paymentRepository.findByTransactionId(callbackResult.getTransactionId())
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Transaction not found: " + callbackResult.getTransactionId()));

        if (transaction.getStatus() != PaymentStatus.PENDING) {
            log.info("[Saga] Ignoring ZaloPay callback for non-pending transaction - txnId={}, status={}",
                    transaction.getTransactionId(), transaction.getStatus());
            return;
        }

        if (callbackResult.getAmount() == null || transaction.getAmount().compareTo(callbackResult.getAmount()) != 0) {
            log.error("[Saga] ZaloPay callback amount mismatch - txnId={}, expected={}, actual={}",
                    transaction.getTransactionId(), transaction.getAmount(), callbackResult.getAmount());
            throw new PaymentException(ErrorCode.VALIDATION_ERROR,
                    "ZaloPay callback amount mismatch for transaction: " + transaction.getTransactionId());
        }

        transaction.markSuccess(
                callbackResult.getZpTransId(),
                "ZaloPay callback: appTransId=" + callbackResult.getAppTransId()
        );
        paymentRepository.save(transaction);
        log.info("[Saga] ZaloPay payment SUCCESS from callback - txnId={}, gatewayTxnId={}",
                transaction.getTransactionId(), callbackResult.getZpTransId());

        outboxService.saveOutboxEventInTx(
                "PaymentTransaction",
                transaction.getTransactionId(),
                "PAYMENT_COMPLETED",
                PaymentCompletedEvent.fromTransaction(
                        transaction.getBookingId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getGatewayTransactionId(),
                        transaction.getPaymentMethod().name()
                )
        );
    }
}
