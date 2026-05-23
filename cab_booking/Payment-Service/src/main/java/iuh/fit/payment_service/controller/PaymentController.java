package iuh.fit.payment_service.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.payment_service.dto.momo.MoMoIpnRequest;
import iuh.fit.payment_service.dto.momo.MoMoIpnResult;
import iuh.fit.payment_service.dto.request.ChargePaymentRequest;
import iuh.fit.payment_service.dto.response.PaymentResponse;
import iuh.fit.payment_service.dto.vnpay.VnPayCallbackResult;
import iuh.fit.payment_service.dto.vnpay.VnPayIpnResponse;
import iuh.fit.payment_service.dto.zalopay.ZaloPayCallbackRequest;
import iuh.fit.payment_service.dto.zalopay.ZaloPayCallbackResponse;
import iuh.fit.payment_service.dto.zalopay.ZaloPayCallbackResult;
import iuh.fit.payment_service.service.MoMoPaymentService;
import iuh.fit.payment_service.service.PaymentSagaService;
import iuh.fit.payment_service.service.VnPayPaymentService;
import iuh.fit.payment_service.service.ZaloPayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Payment API", description = "Payment processing and transaction management APIs")
public class PaymentController {

    private final PaymentSagaService paymentSagaService;
    private final MoMoPaymentService moMoPaymentService;
    private final ZaloPayPaymentService zaloPayPaymentService;
    private final VnPayPaymentService vnPayPaymentService;

    @PostMapping("/charge")
    @Operation(
            summary = "Charge payment",
            description = "Initiates a payment charge for a booking. Supports idempotency via X-Idempotency-Key header or request body."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment processed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or payment declined"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Idempotency conflict - duplicate request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Payment gateway error")
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> chargePayment(
            @Valid @RequestBody ChargePaymentRequest request,
            @Parameter(description = "Idempotency key to prevent duplicate charges")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String headerIdempotencyKey
    ) {
        log.info("[Controller] POST /api/v1/payments/charge - bookingId={}, customerId={}, amount={}, headerIdemKey={}",
                request.getBookingId(), request.getCustomerId(), request.getAmount(), headerIdempotencyKey);

        if (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank()
                && (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())) {
            request.setIdempotencyKey(headerIdempotencyKey);
        }

        PaymentResponse response = paymentSagaService.startPaymentSaga(request);

        String message;
        switch (response.getStatus()) {
            case SUCCESS:
                message = "Payment completed successfully";
                break;
            case FAILED:
            case FAILED_FINAL:
                message = "Payment failed: " + response.getFailureReason();
                break;
            case PENDING:
            case RETRY:
                message = "Payment is being processed";
                break;
            default:
                message = "Payment saga initiated";
        }

        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .message(message)
                .result(response)
                .build());
    }

    @GetMapping("/txn/{transactionId}")
    @Operation(summary = "Get payment by transaction ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByTransactionId(
            @Parameter(description = "Transaction ID", example = "txn_abc123def456")
            @PathVariable String transactionId
    ) {
        log.info("[Controller] GET /api/v1/payments/txn/{}", transactionId);
        PaymentResponse response = paymentSagaService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .message("Payment retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByBookingId(
            @Parameter(description = "Booking ID", example = "b_123")
            @PathVariable String bookingId
    ) {
        log.info("[Controller] GET /api/v1/payments/booking/{}", bookingId);
        PaymentResponse response = paymentSagaService.getPaymentByBookingId(bookingId);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .message("Payment retrieved successfully")
                .result(response)
                .build());
    }

    @PostMapping("/momo/ipn")
    @Operation(
            summary = "MoMo IPN (Instant Payment Notification) webhook",
            description = "Receives payment confirmation callbacks from MoMo after customer completes payment"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "IPN processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid signature")
    })
    public ResponseEntity<Void> handleMoMoIpn(
            @RequestBody MoMoIpnRequest ipnRequest
    ) {
        log.info("[Controller] POST /api/v1/payments/momo/ipn - orderId={}, resultCode={}, amount={}",
                ipnRequest.getOrderId(), ipnRequest.getResultCode(), ipnRequest.getAmount());

        MoMoIpnResult result = moMoPaymentService.processIpn(ipnRequest);
        paymentSagaService.completePaymentFromMoMoIpn(result);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/zalopay/callback")
    @Operation(
            summary = "ZaloPay callback webhook",
            description = "Receives payment confirmation callbacks from ZaloPay after customer completes payment"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callback acknowledged")
    })
    public ResponseEntity<ZaloPayCallbackResponse> handleZaloPayCallback(
            @RequestBody ZaloPayCallbackRequest callbackRequest
    ) {
        log.info("[Controller] POST /api/v1/payments/zalopay/callback - type={}", callbackRequest.getType());

        try {
            ZaloPayCallbackResult result = zaloPayPaymentService.processCallback(callbackRequest);
            paymentSagaService.completePaymentFromZaloPayCallback(result);
            return ResponseEntity.ok(ZaloPayCallbackResponse.success());
        } catch (Exception e) {
            log.error("[Controller] ZaloPay callback processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ZaloPayCallbackResponse.invalid(e.getMessage()));
        }
    }

    @GetMapping("/vnpay/return")
    @Operation(
            summary = "VNPay return URL",
            description = "Receives customer redirect parameters from VNPay after payment completion"
    )
    public ResponseEntity<ApiResponse<PaymentResponse>> handleVnPayReturn(
            @RequestParam Map<String, String> params
    ) {
        log.info("[Controller] GET /api/v1/payments/vnpay/return - txnRef={}, responseCode={}",
                params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));

        VnPayCallbackResult result = vnPayPaymentService.processCallback(params);
        PaymentResponse response = paymentSagaService.completePaymentFromVnPayCallback(result);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .message(result.isSuccess() ? "VNPay payment completed successfully" : "VNPay payment failed")
                .result(response)
                .build());
    }

    @GetMapping("/vnpay/ipn")
    @Operation(
            summary = "VNPay IPN webhook",
            description = "Receives server-to-server payment confirmation from VNPay"
    )
    public ResponseEntity<VnPayIpnResponse> handleVnPayIpn(
            @RequestParam Map<String, String> params
    ) {
        log.info("[Controller] GET /api/v1/payments/vnpay/ipn - txnRef={}, responseCode={}",
                params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));

        try {
            if (params.isEmpty() || !params.containsKey("vnp_SecureHash")) {
                return ResponseEntity.ok(new VnPayIpnResponse("99", "Input data required"));
            }
            VnPayCallbackResult result = vnPayPaymentService.processCallback(params);
            paymentSagaService.completePaymentFromVnPayCallback(result);
            return ResponseEntity.ok(VnPayIpnResponse.success());
        } catch (Exception e) {
            log.error("[Controller] VNPay IPN processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(VnPayIpnResponse.invalid(e.getMessage()));
        }
    }
}
