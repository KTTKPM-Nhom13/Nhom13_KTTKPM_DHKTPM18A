package iuh.fit.payment_service.service;

import iuh.fit.payment_service.dto.event.PaymentCompletedEvent;
import iuh.fit.payment_service.dto.event.PaymentFailedEvent;
import iuh.fit.payment_service.dto.momo.MoMoQueryResponse;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentPollingService {

    private final PaymentTransactionRepository paymentRepository;
    private final MoMoPaymentService moMoPaymentService;
    private final OutboxService outboxService;

    private static final int STALE_THRESHOLD_MINUTES = 10;
    private static final int BATCH_SIZE = 50;
    private static final int MOMO_SUCCESS_CODE = 0;

    @Scheduled(fixedDelayString = "${app.payment.polling.interval-ms:120000}", initialDelayString = "${app.payment.polling.initial-delay-ms:60000}")
    public void pollPendingTransactions() {
        Instant cutoff = Instant.now().minusSeconds(STALE_THRESHOLD_MINUTES * 60L);
        List<PaymentTransaction> stalePending = paymentRepository
                .findByStatusInOrderByCreatedAtDesc(List.of(PaymentStatus.PENDING));

        List<PaymentTransaction> toPoll = stalePending.stream()
                .filter(t -> t.getCreatedAt().isBefore(cutoff))
                .limit(BATCH_SIZE)
                .toList();

        if (toPoll.isEmpty()) {
            return;
        }

        log.info("[Poll] Found {} stale PENDING transactions to poll", toPoll.size());
        for (PaymentTransaction transaction : toPoll) {
            try {
                pollTransaction(transaction);
            } catch (Exception e) {
                log.error("[Poll] Error polling transaction txnId={}: {}",
                        transaction.getTransactionId(), e.getMessage());
            }
        }
    }

    private void pollTransaction(PaymentTransaction transaction) {
        log.info("[Poll] Polling txnId={}, status={}, createdAt={}",
                transaction.getTransactionId(), transaction.getStatus(), transaction.getCreatedAt());

        if (transaction.getPaymentMethod() != PaymentMethod.MOMO) {
            log.debug("[Poll] Skipping non-MoMo transaction txnId={}", transaction.getTransactionId());
            return;
        }

        String requestId = "query_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MoMoQueryResponse queryResponse = moMoPaymentService.queryTransaction(
                transaction.getTransactionId(), requestId);

        if (queryResponse == null) {
            log.warn("[Poll] Query returned null for txnId={}", transaction.getTransactionId());
            return;
        }

        log.info("[Poll] Query result - txnId={}, resultCode={}, message={}, transId={}",
                transaction.getTransactionId(), queryResponse.getResultCode(),
                queryResponse.getMessage(), queryResponse.getTransId());

        if (queryResponse.getResultCode() != null && queryResponse.getResultCode() == MOMO_SUCCESS_CODE) {
            syncSuccessFromQuery(transaction, queryResponse);
        } else if (isTerminalFailure(queryResponse.getResultCode())) {
            syncFailureFromQuery(transaction, queryResponse);
        } else {
            log.debug("[Poll] Transaction still pending at MoMo - txnId={}, resultCode={}",
                    transaction.getTransactionId(), queryResponse.getResultCode());
        }
    }

    @Transactional
    public void syncSuccessFromQuery(PaymentTransaction transaction, MoMoQueryResponse queryResponse) {
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            log.info("[Poll] Transaction already SUCCESS - txnId={}", transaction.getTransactionId());
            return;
        }

        transaction.markSuccess(
                queryResponse.getTransId() != null ? String.valueOf(queryResponse.getTransId()) : null,
                "Polling sync: resultCode=" + queryResponse.getResultCode() + ", message=" + queryResponse.getMessage()
        );
        paymentRepository.save(transaction);
        log.info("[Poll] Transaction synced to SUCCESS - txnId={}, gatewayTxnId={}",
                transaction.getTransactionId(), queryResponse.getTransId());

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
    public void syncFailureFromQuery(PaymentTransaction transaction, MoMoQueryResponse queryResponse) {
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        String reason = "MoMo query: resultCode=" + queryResponse.getResultCode() + ": " + queryResponse.getMessage();
        transaction.markFailed(reason, false);
        paymentRepository.save(transaction);
        log.warn("[Poll] Transaction synced to FAILED - txnId={}, reason={}",
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

    private boolean isTerminalFailure(Integer resultCode) {
        if (resultCode == null) return false;
        return resultCode == 1000 || resultCode == 1001 || resultCode == 1002
                || resultCode == 1003 || resultCode == 1006 || resultCode == 1007
                || resultCode == 1010 || resultCode == 1011 || resultCode == 1014
                || resultCode == 1015 || resultCode == 1023 || resultCode == 2002;
    }
}
