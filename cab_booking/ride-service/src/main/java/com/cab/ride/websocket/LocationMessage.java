package com.cab.ride.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO nhận tọa độ GPS từ Driver App qua STOMP WebSocket.
 * Mapping tự động từ JSON payload của frame STOMP.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationMessage {

    /** ID của tài xế gửi tọa độ. */
    private String driverId;

    /** Vĩ độ hiện tại (latitude). */
    private double lat;

    /** Kinh độ hiện tại (longitude). */
    private double lng;
}
