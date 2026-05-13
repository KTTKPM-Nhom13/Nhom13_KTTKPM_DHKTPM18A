package com.cab.ride.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket + STOMP cho ride-service.
 *
 * <p><b>Endpoint:</b> {@code ws://host:8089/ws/rides} (STOMP over WebSocket)<br>
 * <b>SockJS fallback:</b> {@code http://host:8089/ws/rides} (cho client không hỗ trợ WS native)<br>
 * <b>Send destination:</b> {@code /app/location.update}
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Đăng ký STOMP endpoint mà Driver App sẽ kết nối vào.
     *
     * <ul>
     *   <li>Path: {@code /ws/rides}</li>
     *   <li>SockJS enabled: fallback HTTP long-polling cho môi trường không hỗ trợ WebSocket thuần.</li>
     *   <li>{@code setAllowedOriginPatterns("*")}: tránh lỗi CORS khi client ở domain khác
     *       (production nên thu hẹp lại theo domain cụ thể).</li>
     * </ul>
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/rides")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Cấu hình message broker.
     *
     * <ul>
     *   <li>{@code /app} — prefix cho các message gửi từ client đến {@code @MessageMapping} handler.</li>
     *   <li>Simple in-memory broker với prefix {@code /topic} — cho phép server broadcast
     *       ngược về client nếu cần (dùng {@code @SendTo("/topic/...")} sau này).</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix để client gửi message đến server (ví dụ: /app/location.update)
        registry.setApplicationDestinationPrefixes("/app");

        // In-memory broker cho server broadcast (ví dụ: /topic/ride-status)
        registry.enableSimpleBroker("/topic");
    }
}
