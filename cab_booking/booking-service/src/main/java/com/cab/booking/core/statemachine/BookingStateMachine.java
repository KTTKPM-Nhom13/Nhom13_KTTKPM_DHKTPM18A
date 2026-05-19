package com.cab.booking.core.statemachine;

import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class BookingStateMachine {

    /**
     * Chuyển đổi trạng thái của Booking.
     * Sử dụng Java 21 Pattern Matching (switch expression) để kiểm tra luồng chuyển trạng thái.
     *
     * @param booking    Booking hiện tại
     * @param nextStatus Trạng thái muốn chuyển sang
     * @throws AppException Nếu chuyển đổi trạng thái không hợp lệ
     */
    public void transitionTo(Booking booking, BookingStatus nextStatus) {
        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == null) {
            currentStatus = BookingStatus.CREATED;
        }

        // Vòng đời: CREATED/REQUESTED → MATCHING → ASSIGNED → ACCEPTED → PICKUP → IN_PROGRESS → COMPLETED → PAID
        // Ngoài ra, tất cả các trạng thái (trừ CREATED) đều có thể chuyển sang CANCELLED
        boolean isValid = switch (currentStatus) {
            case CREATED -> nextStatus == BookingStatus.MATCHING || nextStatus == BookingStatus.ASSIGNED;
            case MATCHING -> nextStatus == BookingStatus.ASSIGNED || nextStatus == BookingStatus.CANCELLED;
            case ASSIGNED -> nextStatus == BookingStatus.ACCEPTED
                    || nextStatus == BookingStatus.MATCHING
                    || nextStatus == BookingStatus.CANCELLED;
            case ACCEPTED -> nextStatus == BookingStatus.PICKUP || nextStatus == BookingStatus.CANCELLED;
            case PICKUP -> nextStatus == BookingStatus.IN_PROGRESS || nextStatus == BookingStatus.CANCELLED;
            case IN_PROGRESS -> nextStatus == BookingStatus.COMPLETED || nextStatus == BookingStatus.CANCELLED;
            case COMPLETED -> nextStatus == BookingStatus.PAID || nextStatus == BookingStatus.CANCELLED;
            case PAID, CANCELLED -> false;
            case null -> false;
        };

        if (!isValid) {
            throw new AppException(ErrorCode.INVALID_STATE);
        }

        booking.setStatus(nextStatus);
    }
}
