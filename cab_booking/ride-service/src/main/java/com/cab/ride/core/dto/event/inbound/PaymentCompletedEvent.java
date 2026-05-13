package com.cab.ride.core.dto.event.inbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound event từ topic {@code payment.completed} — được publish bởi payment-service
 * khi thanh toán thành công cho một chuyến đi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    /** ID của cuốc xe (UUID dưới dạng String). */
    private String rideId;

    /** ID định danh sự kiện thanh toán — dùng cho idempotency. */
    private String eventId;

    /** Số tiền đã thanh toán. */
    private Double amount;
}
