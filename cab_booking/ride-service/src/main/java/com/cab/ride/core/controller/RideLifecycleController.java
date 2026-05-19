package com.cab.ride.core.controller;

import com.cab.ride.core.dto.request.CompleteRideRequest;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.service.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideLifecycleController {

    private final RideService rideService;

    @PostMapping("/{rideId}/arrive")
    public ResponseEntity<Ride> arrive(@AuthenticationPrincipal Jwt jwt, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.arriveAtPickup(rideId, currentDriverId(jwt)));
    }

    @PostMapping("/{rideId}/start")
    public ResponseEntity<Ride> start(@AuthenticationPrincipal Jwt jwt, @PathVariable String rideId) {
        return ResponseEntity.ok(rideService.startRide(rideId, currentDriverId(jwt)));
    }

    @PostMapping("/{rideId}/complete")
    public ResponseEntity<Ride> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String rideId,
            @RequestBody(required = false) CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.completeRide(rideId, currentDriverId(jwt), request));
    }

    private String currentDriverId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Driver identity is required");
        }
        return jwt.getSubject();
    }
}
