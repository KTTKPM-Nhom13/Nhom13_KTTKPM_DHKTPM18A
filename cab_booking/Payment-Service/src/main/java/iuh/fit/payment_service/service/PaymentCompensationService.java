package iuh.fit.payment_service.service;

import iuh.fit.payment_service.dto.event.PaymentRefundedEvent;
import iuh.fit.payment_service.dto.momo.MoMoRefundResponse;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.exception.ErrorCode;
import iuh.fit.payment_service.exception.PaymentException;
import iuh.fit.payment_service.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationService {

    private final PaymentTransactionRepository paymentRepository;
    private final MoMoPaymentService moMoPaymentService;
    private final OutboxService outboxService;

    private static final int MOMO_REFUND_SUCCESS_CODE = 0;

    @Transactional
    public void compensatePayment(String transactionId, String reason) {
        log.info("[Compensate] Starting compensation - txnId={}, reason={}", transactionId, reason);

        PaymentTransaction transaction = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Transaction not found for compensation: " + transactionId));
        doCompensate(transaction, reason);
    }

    @Transactional
    public void compensatePaymentByBookingId(String bookingId, String reason) {
        log.info("[Compensate] Starting compensation by bookingId - bookingId={}, reason={}", bookingId, reason);

        PaymentTransaction transaction = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for booking: " + bookingId));
        doCompensate(transaction, reason);
    }

    private void doCompensate(PaymentTransaction transaction, String reason) {
        if (transaction.getStatus() == PaymentStatus.REFUNDED
                || transaction.getStatus() == PaymentStatus.REFUND_PENDING) {
            log.info("[Compensate] Transaction already refunded/pending - txnId={}, status={}",
                    transaction.getTransactionId(), transaction.getStatus());
            return;
        }

        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            log.info("[Compensate] Transaction not in SUCCESS state, skipping refund - txnId={}, status={}",
                    transaction.getTransactionId(), transaction.getStatus());
            return;
        }

        if (transaction.getGatewayTransactionId() == null || transaction.getGatewayTransactionId().isBlank()) {
            log.warn("[Compensate] No gateway transaction ID - txnId={}, cannot refund via MoMo",
                    transaction.getTransactionId());
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setFailureReason("Compensation skipped: no gateway txn ID - " + reason);
            paymentRepository.save(transaction);
            return;
        }

        transaction.setStatus(PaymentStatus.REFUND_PENDING);
        paymentRepository.save(transaction);
        log.info("[Compensate] Marked REFUND_PENDING - txnId={}", transaction.getTransactionId());

        try {
            executeRefund(transaction, reason);
        } catch (Exception e) {
            log.error("[Compensate] Refund failed - txnId={}: {}", transaction.getTransactionId(), e.getMessage());
            transaction.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(transaction);
            log.warn("[Compensate] Reverted to SUCCESS - txnId={}", transaction.getTransactionId());
        }
    }

    private void executeRefund(PaymentTransaction transaction, String reason) {
        if (transaction.getPaymentMethod() == PaymentMethod.ZALOPAY) {
            throw new UnsupportedOperationException("ZaloPay refund is not implemented yet");
        }

        Long gatewayTransId;
        try {
            gatewayTransId = Long.parseLong(transaction.getGatewayTransactionId());
        } catch (NumberFormatException e) {
            log.error("[Compensate] Invalid gateway transaction ID - txnId={}, gatewayTxnId={}",
                    transaction.getTransactionId(), transaction.getGatewayTransactionId());
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setFailureReason("Refund failed: invalid gateway transaction ID - " + reason);
            paymentRepository.save(transaction);
            return;
        }

        String refundRequestId = "refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String description = "Compensation: " + reason;

        MoMoRefundResponse refundResponse;
        if (transaction.getPaymentMethod() == PaymentMethod.MOMO) {
            refundResponse = moMoPaymentService.refund(
                    gatewayTransId,
                    transaction.getTransactionId(),
                    transaction.getAmount().longValue(),
                    refundRequestId,
                    description
            );
        } else {
            refundResponse = buildMockRefundResponse(transaction, refundRequestId);
        }

        handleRefundResponse(transaction, refundResponse, reason);
    }

    private MoMoRefundResponse buildMockRefundResponse(PaymentTransaction transaction, String requestId) {
        return MoMoRefundResponse.builder()
                .partnerCode("MOCK")
                .requestId(requestId)
                .orderId(transaction.getTransactionId())
                .amount(transaction.getAmount().longValue())
                .transId(System.currentTimeMillis())
                .resultCode(MOMO_REFUND_SUCCESS_CODE)
                .message("Mock refund success")
                .responseTime(System.currentTimeMillis())
                .build();
    }

    @Transactional
    public void handleRefundResponse(PaymentTransaction transaction, MoMoRefundResponse refundResponse, String reason) {
        if (refundResponse.getResultCode() != null && refundResponse.getResultCode() == MOMO_REFUND_SUCCESS_CODE) {
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setFailureReason("Refunded via MoMo - " + reason);
            paymentRepository.save(transaction);
            log.info("[Compensate] Refund SUCCESS - txnId={}, refundTransId={}",
                    transaction.getTransactionId(), refundResponse.getTransId());

            outboxService.saveOutboxEventInTx(
                    "PaymentTransaction",
                    transaction.getTransactionId(),
                    "PAYMENT_REFUNDED",
                    PaymentRefundedEvent.fromTransaction(
                            transaction.getBookingId(),
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            refundResponse.getTransId() != null ? String.valueOf(refundResponse.getTransId()) : null,
                            reason
                    )
            );
        } else {
            transaction.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(transaction);
            log.error("[Compensate] Refund FAILED from MoMo - txnId={}, resultCode={}, message={}",
                    transaction.getTransactionId(), refundResponse.getResultCode(), refundResponse.getMessage());
        }
    }
}
