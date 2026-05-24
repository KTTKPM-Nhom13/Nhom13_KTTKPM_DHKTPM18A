package com.cab.ride.core.service.socket;

import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.repository.RideRepository;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Manages Socket.IO room join/leave operations with ride ownership validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RideSocketRoomService {

    private final RideRepository rideRepository;

    /**
     * Validates that the client has permission to join the ride room,
     * then joins them into the room {@code ride:{rideId}}.
     *
     * @return the Ride if join succeeded, null if validation failed
     */
    public Ride joinRide(SocketIOClient client, String userId, String rideId) {
        Ride ride = rideRepository.findById(parseUuid(rideId)).orElse(null);
        if (ride == null) {
            log.warn("[RideSocket] join_ride failed: ride not found, rideId={}", rideId);
            return null;
        }

        boolean isAssignedDriver = userId.equals(ride.getDriverId());
        boolean isCustomerOwner = userId.equals(ride.getCustomerId());

        if (!isAssignedDriver && !isCustomerOwner) {
            log.warn("[RideSocket] join_ride forbidden: userId={} is not driver or customer of rideId={}", userId, rideId);
            return null;
        }

        String roomName = roomName(rideId);
        client.joinRoom(roomName);
        log.info("[RideSocket] userId={} joined room={} sessionId={}", userId, roomName, client.getSessionId());
        return ride;
    }

    /**
     * Removes the client from the ride room.
     */
    public void leaveRide(SocketIOClient client, String rideId) {
        String roomName = roomName(rideId);
        client.leaveRoom(roomName);
        log.info("[RideSocket] Client left room={} sessionId={}", roomName, client.getSessionId());
    }

    /**
     * Broadcasts an event to all clients in the ride room.
     */
    public void broadcastToRide(SocketIOServer server, String rideId, String eventName, Object data) {
        String roomName = roomName(rideId);
        server.getRoomOperations(roomName).sendEvent(eventName, data);
        log.debug("[RideSocket] Broadcasted '{}' to room={}", eventName, roomName);
    }

    static String roomName(String rideId) {
        return "ride:" + rideId;
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
