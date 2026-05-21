package com.cab.ride.core.enums;

/**
 * Vòng đời chuẩn của một chuyến đi (State Machine).
 *
 * <pre>
 * CREATED → MATCHING → ASSIGNED → PICKUP → IN_PROGRESS → COMPLETED → PAID
 *                                     ↘                              ↗
 *                                       CANCELLED (bất kỳ bước nào)
 * </pre>
 */
public enum RideStatus {

    /** Cuốc xe vừa được tạo, chưa tìm tài xế. */
    CREATED,

    /** Đang gửi yêu cầu tới matching-service để tìm tài xế phù hợp. */
    MATCHING,

    /** Tài xế đã được chỉ định, đang trên đường đến đón khách. */
    ASSIGNED,

    ACCEPTED,

    /** Tài xế đã đến điểm đón, chờ khách lên xe. */
    PICKUP,

    /** Chuyến đi đang diễn ra (khách đang trên xe). */
    IN_PROGRESS,

    /** Chuyến đi hoàn thành, chờ thanh toán. */
    COMPLETED,

    /** Thanh toán thành công — trạng thái kết thúc bình thường. */
    PAID,

    /** Cuốc xe bị huỷ — trạng thái kết thúc bất thường. */
    CANCELLED
}
