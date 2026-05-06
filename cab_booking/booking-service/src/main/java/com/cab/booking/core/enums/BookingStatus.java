package com.cab.booking.core.enums;

/**
 * Ride State Machine — Phụ lục C.1.
 * Vòng đời của một cuốc xe tuân thủ chuỗi trạng thái nghiêm ngặt:
 * CREATED → MATCHING → ASSIGNED → PICKUP → IN_PROGRESS → COMPLETED → PAID
 * Mọi trạng thái (trừ CREATED) đều có thể chuyển sang CANCELLED.
 */
public enum BookingStatus {
    CREATED,       // Vừa khởi tạo
    MATCHING,      // Đang tìm tài xế
    ASSIGNED,      // Đã gán tài xế
    PICKUP,        // Tài xế đang đến đón
    IN_PROGRESS,   // Đang di chuyển
    COMPLETED,     // Đã đến nơi
    PAID,          // Đã thanh toán
    CANCELLED      // Đã hủy
}
