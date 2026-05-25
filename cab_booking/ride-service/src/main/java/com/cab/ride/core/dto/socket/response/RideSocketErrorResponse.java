package com.cab.ride.core.dto.socket.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard error response emitted to client on {@code socket_error} event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideSocketErrorResponse {

    private String code;
    private String message;
}
