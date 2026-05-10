package iuh.fit.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.payment_service.config.KafkaConfig;
import iuh.fit.payment_service.config.RedisConfig.IdempotencyRedisService;
import iuh.fit.payment_service.dto.event.PaymentCompletedEvent;
import iuh.fit.payment_service.dto.event.PaymentFailedEvent;
import iuh.fit.payment_service.dto.request.ChargePaymentRequest;
import iuh.fit.payment_service.dto.request.GatewayChargeRequest;
import iuh.fit.payment_service.dto.response.GatewayChargeResponse;
import iuh.fit.payment_service.dto.response.PaymentResponse;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.exception.ErrorCode;
import iuh.fit.payment_service.exception.IdempotencyConflictException;
import iuh.fit.payment_service.exception.PaymentException;
import iuh.fit.payment_service.exception.PaymentGatewayException;
import iuh.fit.payment_service.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaService {

    private final PaymentTransactionRepository paymentRepository;
    private final IdempotencyRedisService idempotencyRedisService;
    private final MockPaymentGatewayService gatewayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY = 3;

    @Transactional
    public PaymentResponse startPaymentSaga(ChargePaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        log.info("[Saga] Starting payment saga - rideId={}, customerId={}, amount={}, method={}, idempotencyKey={}",
                request.getRideId(), request.getCustomerId(), request.getAmount(), request.getPaymentMethod(), idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return handleIdempotency(idempotencyKey, request);
        }

        PaymentTransaction transaction = createAndSaveTransaction(request);
        return executePayment(transaction);
    }

    private PaymentResponse handleIdempotency(String idempotencyKey, ChargePaymentRequest request) {
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

    private PaymentTransaction createAndSaveTransaction(ChargePaymentRequest request) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId("txn_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .rideId(request.getRideId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .retryCount(0)
                .paymentGatewayName("MOCK_GATEWAY")
                .build();

        transaction = paymentRepository.save(transaction);
        log.info("[Saga] Transaction created - txnId={}, status=PENDING", transaction.getTransactionId());
        return transaction;
    }

    private PaymentResponse executePayment(PaymentTransaction transaction) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY) {
            attempt++;
            transaction.setRetryCount(attempt - 1);
            log.info("[Saga] Payment attempt {}/{} - txnId={}", attempt, MAX_RETRY, transaction.getTransactionId());

            try {
                GatewayChargeResponse gatewayResponse = callPaymentGateway(transaction);

                if (gatewayResponse.isSuccess()) {
                    transaction.markSuccess(gatewayResponse.getGatewayTransactionId(),
                            objectMapper.writeValueAsString(gatewayResponse));
                    transaction = paymentRepository.save(transaction);
                    log.info("[Saga] Payment SUCCESS - txnId={}, gatewayTxnId={}",
                            transaction.getTransactionId(), gatewayResponse.getGatewayTransactionId());

                    publishPaymentCompletedEvent(transaction);
                    return PaymentResponse.fromEntity(transaction, "Payment completed successfully");
                }

                String reason = gatewayResponse.getErrorCode() + ": " + gatewayResponse.getMessage();
                lastException = new PaymentGatewayException(gatewayResponse.getErrorCode(), reason);
                log.warn("[Saga] Gateway returned failure - txnId={}, error={}",
                        transaction.getTransactionId(), reason);

                if (attempt >= MAX_RETRY) {
                    transaction.markFailed(reason, false);
                    transaction = paymentRepository.save(transaction);
                    log.error("[Saga] Payment FAILED_FINAL after {} attempts - txnId={}",
                            attempt, transaction.getTransactionId());
                    publishPaymentFailedEvent(transaction);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + reason);
                }

                transaction.markFailed(reason, true);
                transaction = paymentRepository.save(transaction);
                log.warn("[Saga] Payment failed, scheduling retry - txnId={}, retryCount={}",
                        transaction.getTransactionId(), transaction.getRetryCount());
                waitBeforeRetry(attempt);

            } catch (PaymentGatewayException e) {
                lastException = e;
                log.error("[Saga] Gateway exception on attempt {} - txnId={}, error={}",
                        attempt, transaction.getTransactionId(), e.getMessage());

                if (attempt >= MAX_RETRY) {
                    transaction.markFailed(e.getGatewayMessage(), false);
                    transaction = paymentRepository.save(transaction);
                    publishPaymentFailedEvent(transaction);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + e.getGatewayMessage());
                }

                transaction.markFailed(e.getGatewayMessage(), true);
                paymentRepository.save(transaction);
                waitBeforeRetry(attempt);

            } catch (Exception e) {
                lastException = e;
                log.error("[Saga] Unexpected exception on attempt {} - txnId={}", attempt, transaction.getTransactionId(), e);

                if (attempt >= MAX_RETRY) {
                    transaction.markFailed("Unexpected error: " + e.getMessage(), false);
                    transaction = paymentRepository.save(transaction);
                    publishPaymentFailedEvent(transaction);
                    return PaymentResponse.fromEntity(transaction, "Payment failed: " + e.getMessage());
                }

                transaction.markFailed("Unexpected error: " + e.getMessage(), true);
                paymentRepository.save(transaction);
                waitBeforeRetry(attempt);
            }
        }

        transaction.setStatus(PaymentStatus.FAILED_FINAL);
        transaction = paymentRepository.save(transaction);
        publishPaymentFailedEvent(transaction);
        return PaymentResponse.fromEntity(transaction, "Payment failed after max retries");
    }

    private GatewayChargeResponse callPaymentGateway(PaymentTransaction transaction) {
        GatewayChargeRequest gatewayRequest = GatewayChargeRequest.builder()
                .transactionId(transaction.getTransactionId())
                .customerId(transaction.getCustomerId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .description("Payment for ride " + transaction.getRideId())
                .build();

        return gatewayService.charge(gatewayRequest);
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

    private void publishPaymentCompletedEvent(PaymentTransaction transaction) {
        try {
            PaymentCompletedEvent event = PaymentCompletedEvent.fromTransaction(
                    transaction.getTransactionId(),
                    transaction.getRideId(),
                    transaction.getCustomerId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getGatewayTransactionId(),
                    transaction.getPaymentMethod().name()
            );

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    KafkaConfig.TOPIC_PAYMENT_COMPLETED,
                    transaction.getTransactionId(),
                    event
            );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("[Saga] Published payment.completed - txnId={}, partition={}, offset={}",
                            transaction.getTransactionId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("[Saga] Failed to publish payment.completed - txnId={}",
                            transaction.getTransactionId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("[Saga] Error publishing payment.completed event - txnId={}",
                    transaction.getTransactionId(), e);
        }
    }

    private void publishPaymentFailedEvent(PaymentTransaction transaction) {
        try {
            PaymentFailedEvent event = PaymentFailedEvent.fromTransaction(
                    transaction.getTransactionId(),
                    transaction.getRideId(),
                    transaction.getCustomerId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getFailureReason(),
                    transaction.getRetryCount()
            );

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    KafkaConfig.TOPIC_PAYMENT_FAILED,
                    transaction.getTransactionId(),
                    event
            );

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("[Saga] Published payment.failed - txnId={}, partition={}, offset={}",
                            transaction.getTransactionId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("[Saga] Failed to publish payment.failed - txnId={}",
                            transaction.getTransactionId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("[Saga] Error publishing payment.failed event - txnId={}",
                    transaction.getTransactionId(), e);
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
    public PaymentResponse getPaymentByRideId(String rideId) {
        log.debug("[Saga] Fetching payment by rideId={}", rideId);
        PaymentTransaction transaction = paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found for ride: " + rideId));
        return PaymentResponse.fromEntity(transaction);
    }
}
