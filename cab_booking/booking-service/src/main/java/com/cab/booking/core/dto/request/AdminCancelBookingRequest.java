package com.cab.booking.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCancelBookingRequest {
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
