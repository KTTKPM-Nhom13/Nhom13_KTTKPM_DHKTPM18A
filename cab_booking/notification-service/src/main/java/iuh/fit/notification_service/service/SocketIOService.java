package iuh.fit.notification_service.service;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketIOService {
    private final SocketIOServer server;
    
    // userId -> sessionId (as string)
    private static final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void startServer() {
        server.addConnectListener(client -> {
            var params = client.getHandshakeData().getUrlParams();
            String userId = params.containsKey("userId") ? params.get("userId").get(0) : null;
            if (userId != null) {
                userSessions.put(userId, client.getSessionId().toString());
                log.info("User {} connected with sessionId {}", userId, client.getSessionId());
            }
        });

        server.addDisconnectListener(client -> {
            var params = client.getHandshakeData().getUrlParams();
            String userId = params.containsKey("userId") ? params.get("userId").get(0) : null;
            if (userId != null) {
                userSessions.remove(userId);
                log.info("User {} disconnected", userId);
            }
        });

        // Event listener for clients joining a booking room
        server.addEventListener("join_room", String.class, (client, bookingId, ackRequest) -> {
            if (bookingId != null && !bookingId.trim().isEmpty()) {
                client.joinRoom(bookingId);
                log.info("Client Session {} joined room: {}", client.getSessionId(), bookingId);
                client.sendEvent("joined_room_success", Map.of("bookingId", bookingId, "status", "success"));
            }
        });

        // Event listener for clients leaving a booking room
        server.addEventListener("leave_room", String.class, (client, bookingId, ackRequest) -> {
            if (bookingId != null && !bookingId.trim().isEmpty()) {
                client.leaveRoom(bookingId);
                log.info("Client Session {} left room: {}", client.getSessionId(), bookingId);
                client.sendEvent("left_room_success", Map.of("bookingId", bookingId, "status", "success"));
            }
        });

        server.start();
        log.info("Socket.io server started on port {}", server.getConfiguration().getPort());
    }

    @PreDestroy
    public void stopServer() {
        server.stop();
    }

    public void sendNotification(String userId, String eventName, Object data) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null) {
            server.getClient(java.util.UUID.fromString(sessionId)).sendEvent(eventName, data);
            log.info("Sent event '{}' to user {}", eventName, userId);
        } else {
            log.debug("User {} is not connected via Socket.io", userId);
        }
    }

    /**
     * Broadcasts an event to all connected clients in a specific booking room
     */
    public void broadcastToBookingRoom(String bookingId, String eventName, Object data) {
        if (bookingId != null && !bookingId.trim().isEmpty()) {
            server.getRoomOperations(bookingId).sendEvent(eventName, data);
            log.info("Broadcasted event '{}' to room '{}'", eventName, bookingId);
        }
    }
}
