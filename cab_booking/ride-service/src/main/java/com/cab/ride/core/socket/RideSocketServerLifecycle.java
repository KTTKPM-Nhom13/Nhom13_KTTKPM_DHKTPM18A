package com.cab.ride.core.socket;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Manages the Socket.IO server lifecycle tied to the Spring application context.
 *
 * <p>Starts the Netty-based SocketIOServer after Spring beans are initialized,
 * and gracefully shuts it down on application stop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideSocketServerLifecycle {

    private final SocketIOServer server;
    private final RideSocketEventHandler eventHandler;

    @PostConstruct
    public void start() {
        eventHandler.registerListeners(server);
        server.start();
        log.info("[RideSocket] Socket.IO server started on port {}", server.getConfiguration().getPort());
    }

    @PreDestroy
    public void stop() {
        server.stop();
        log.info("[RideSocket] Socket.IO server stopped");
    }
}
