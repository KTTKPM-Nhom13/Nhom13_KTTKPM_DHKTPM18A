package com.cab.booking.core.statemachine;

import com.cab.booking.core.entity.Booking;
import com.cab.booking.core.enums.BookingStatus;
import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class BookingStateMachine {

    public void transitionTo(Booking booking, BookingStatus nextStatus) {
        BookingStatus currentStatus = booking.getStatus();
        if (currentStatus == null) {
            currentStatus = BookingStatus.CREATED;
        }

        boolean isValid = switch (currentStatus) {
            case CREATED -> nextStatus == BookingStatus.MATCHING
                    || nextStatus == BookingStatus.PENDING_PAYMENT
                    || nextStatus == BookingStatus.ASSIGNED;
            case PENDING_PAYMENT -> nextStatus == BookingStatus.MATCHING
                    || nextStatus == BookingStatus.CANCELLED;
            case MATCHING -> nextStatus == BookingStatus.ASSIGNED
                    || nextStatus == BookingStatus.CANCELLED;
            case ASSIGNED -> nextStatus == BookingStatus.ACCEPTED
                    || nextStatus == BookingStatus.MATCHING
                    || nextStatus == BookingStatus.CANCELLED;
            case ACCEPTED -> nextStatus == BookingStatus.PICKUP
                    || nextStatus == BookingStatus.CANCELLED;
            case PICKUP -> nextStatus == BookingStatus.IN_PROGRESS
                    || nextStatus == BookingStatus.CANCELLED;
            case IN_PROGRESS -> nextStatus == BookingStatus.COMPLETED
                    || nextStatus == BookingStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
            case null -> false;
        };

        if (!isValid) {
            throw new AppException(ErrorCode.INVALID_STATE);
        }

        booking.setStatus(nextStatus);
    }
}
