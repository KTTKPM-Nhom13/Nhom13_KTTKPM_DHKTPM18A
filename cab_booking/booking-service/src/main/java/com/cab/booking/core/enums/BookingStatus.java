package com.cab.booking.core.enums;

/**
 * Ride State Machine — Phụ lục C.1.
 * Vòng đời của một cuốc xe tuân thủ chuỗi trạng thái nghiêm ngặt:
 * CREATED/REQUESTED → MATCHING → ASSIGNED → ACCEPTED → PICKUP → IN_PROGRESS → COMPLETED → PAID
 * Mọi trạng thái (trừ CREATED) đều có thể chuyển sang CANCELLED.
 */
public enum BookingStatus {
    CREATED,       // Vừa khởi tạo
    MATCHING,      // Đang tìm tài xế (Internal State)
    ASSIGNED,      // Đã gán tài xế (Internal State)
    ACCEPTED,      // Tài xế đã xác nhận cuốc
    PICKUP,        // Tài xế đang đến đón
    IN_PROGRESS,   // Đang di chuyển
    COMPLETED,     // Đã đến nơi
    PAID,          // Đã thanh toán
    CANCELLED      // Đã hủy
}
