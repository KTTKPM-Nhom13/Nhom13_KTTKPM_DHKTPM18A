package com.cab.ride.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Socket.IO v4 server configuration for ride realtime tracking.
 *
 * <p>Runs on a standalone Netty port (default 9095), separate from the Spring HTTP port (8085).
 * Supports Socket.IO v4 / Engine.IO v4 (EIO=4) protocol.
 *
 * <p>Clients connect via:
 * <pre>
 *   io("ws://host:9095", { auth: { token: jwt }, transports: ["websocket"] })
 * </pre>
 *
 * <p>Backward-compatible query parameter also supported:
 * <pre>
 *   ws://host:9095/socket.io/?EIO=4&transport=websocket&token=<JWT>
 * </pre>
 */
@Slf4j
@org.springframework.context.annotation.Configuration
public class RideSocketConfig {

    @Value("${ride-socket.host:0.0.0.0}")
    private String host;

    @Value("${ride-socket.port:9095}")
    private int port;

    @Value("${ride-socket.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SocketIOServer rideSocketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setOrigin(allowedOrigins);
        config.setPingInterval(25_000);
        config.setPingTimeout(60_000);
        config.setMaxFramePayloadLength(1024 * 1024);
        config.setMaxHttpContentLength(1024 * 1024);

        SocketIOServer server = new SocketIOServer(config);
        log.info("[RideSocket] SocketIOServer configured (Socket.IO v4 / EIO=4): host={} port={}", host, port);
        return server;
    }
}
