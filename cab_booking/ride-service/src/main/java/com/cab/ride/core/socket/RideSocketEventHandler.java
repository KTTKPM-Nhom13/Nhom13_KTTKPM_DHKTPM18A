package com.cab.ride.core.socket;

import com.cab.ride.core.dto.socket.request.RideLocationSocketRequest;
import com.cab.ride.core.dto.socket.response.DriverLocationUpdatedResponse;
import com.cab.ride.core.dto.socket.response.RideSocketErrorResponse;
import com.cab.ride.core.service.RideLocationService;
import com.cab.ride.core.service.socket.RideSocketAuthService;
import com.cab.ride.core.service.socket.RideSocketRoomService;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registers all Socket.IO v4 event listeners on the server.
 *
 * <p>Events handled:
 * <ul>
 *   <li>{@code connect} — authenticate client via JWT</li>
 *   <li>{@code join_ride} — client joins ride room</li>
 *   <li>{@code leave_ride} — client leaves ride room</li>
 *   <li>{@code driver.location.update} — driver sends GPS, validated, Redis + Kafka + broadcast</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideSocketEventHandler {

    private static final String EVENT_JOIN_RIDE = "join_ride";
    private static final String EVENT_LEAVE_RIDE = "leave_ride";
    private static final String EVENT_DRIVER_LOCATION_UPDATE = "driver.location.update";
    private static final String EVENT_JOINED_RIDE = "joined_ride";
    private static final String EVENT_LEFT_RIDE = "left_ride";
    private static final String EVENT_DRIVER_LOCATION_UPDATED = "driver.location.updated";
    private static final String EVENT_SOCKET_ERROR = "socket_error";

    private final RideSocketAuthService authService;
    private final RideSocketRoomService roomService;
    private final RideLocationService rideLocationService;

    /**
     * Registers all event listeners on the SocketIOServer.
     * Called by {@link RideSocketServerLifecycle} before server start.
     */
    public void registerListeners(SocketIOServer server) {

        // ── Connect: authenticate via JWT ────────────────────────────────────
        server.addConnectListener(client -> {
            boolean authenticated = authService.authenticate(client);
            if (!authenticated) {
                emitError(client, "UNAUTHORIZED", "Authentication failed: invalid or missing JWT token");
                client.disconnect();
            }
        });

        server.addDisconnectListener(client -> {
            log.info("[RideSocket] Client disconnected: sessionId={}", client.getSessionId());
        });

        // ── join_ride ────────────────────────────────────────────────────────
        server.addEventListener(EVENT_JOIN_RIDE, Map.class, (client, data, ack) -> {
            String userId = authService.getUserId(client).orElse(null);
            if (userId == null) {
                emitError(client, "UNAUTHORIZED", "Not authenticated");
                return;
            }

            String rideId = getString(data, "rideId");
            if (rideId == null) {
                emitError(client, "BAD_REQUEST", "rideId is required");
                return;
            }

            var ride = roomService.joinRide(client, userId, rideId);
            if (ride == null) {
                emitError(client, "FORBIDDEN", "You are not authorized to join this ride");
                return;
            }

            client.sendEvent(EVENT_JOINED_RIDE, Map.of("rideId", rideId, "status", "JOINED"));
        });

        // ── leave_ride ───────────────────────────────────────────────────────
        server.addEventListener(EVENT_LEAVE_RIDE, Map.class, (client, data, ack) -> {
            String rideId = getString(data, "rideId");
            if (rideId == null) {
                emitError(client, "BAD_REQUEST", "rideId is required");
                return;
            }

            roomService.leaveRide(client, rideId);
            client.sendEvent(EVENT_LEFT_RIDE, Map.of("rideId", rideId, "status", "LEFT"));
        });

        // ── driver.location.update ───────────────────────────────────────────
        server.addEventListener(EVENT_DRIVER_LOCATION_UPDATE, RideLocationSocketRequest.class,
                new DataListener<RideLocationSocketRequest>() {
                    @Override
                    public void onData(SocketIOClient client, RideLocationSocketRequest request, AckRequest ack) {
                        handleDriverLocationUpdate(client, request, server);
                    }
                });
    }

    private void handleDriverLocationUpdate(SocketIOClient client, RideLocationSocketRequest request,
                                             SocketIOServer server) {
        // 1. Must be authenticated
        String driverId = authService.getUserId(client).orElse(null);
        if (driverId == null) {
            emitError(client, "UNAUTHORIZED", "Not authenticated");
            return;
        }

        // 2. Must have DRIVER role
        if (!authService.hasRole(client, "DRIVER")) {
            emitError(client, "FORBIDDEN", "Only drivers can send location updates");
            return;
        }

        // 3. Validate request fields
        if (request.getRideId() == null || request.getRideId().isBlank()) {
            emitError(client, "BAD_REQUEST", "rideId is required");
            return;
        }

        try {
            // 4. Delegate to shared service: validates ride, updates Redis, publishes Kafka
            DriverLocationUpdatedResponse response = rideLocationService.updateLocation(
                    driverId,
                    request.getRideId(),
                    request.getLat(),
                    request.getLng(),
                    request.getHeading(),
                    request.getSpeed()
            );

            // 5. Broadcast to ride room
            roomService.broadcastToRide(server, request.getRideId(), EVENT_DRIVER_LOCATION_UPDATED, response);

        } catch (RideLocationService.LocationValidationException ex) {
            log.warn("[RideSocket] Location update validation failed: driverId={} rideId={} error={}",
                    driverId, request.getRideId(), ex.getMessage());
            emitError(client, ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("[RideSocket] Location update error: driverId={} rideId={} error={}",
                    driverId, request.getRideId(), ex.getMessage(), ex);
            emitError(client, "INTERNAL_ERROR", "Failed to process location update");
        }
    }

    private void emitError(SocketIOClient client, String code, String message) {
        client.sendEvent(EVENT_SOCKET_ERROR, RideSocketErrorResponse.builder()
                .code(code)
                .message(message)
                .build());
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
}
