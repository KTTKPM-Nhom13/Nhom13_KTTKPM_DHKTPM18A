package com.cab.booking.core.dto.request;

import com.cab.booking.core.enums.BookingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateBookingStatusRequest {
    @NotNull
    private BookingStatus status;

    @NotBlank
    @Size(max = 1000)
    private String reason;
}
