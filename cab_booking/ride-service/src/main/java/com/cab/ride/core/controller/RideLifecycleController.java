package com.cab.ride.core.controller;

import com.cab.ride.core.dto.request.CompleteRideRequest;
import com.cab.ride.core.dto.response.RideResponse;
import com.cab.ride.core.service.RideService;
import iuh.fit.common.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class RideLifecycleController {

    private final RideService rideService;

    @PostMapping("/{rideId}/arrive")
    public ApiResponse<RideResponse> arrive(@AuthenticationPrincipal Jwt jwt, @PathVariable String rideId) {
        return ApiResponse.success("Driver arrived at pickup",
                RideResponse.fromEntity(rideService.arriveAtPickup(rideId, currentDriverId(jwt))));
    }

    @PostMapping("/{rideId}/start")
    public ApiResponse<RideResponse> start(@AuthenticationPrincipal Jwt jwt, @PathVariable String rideId) {
        return ApiResponse.success("Ride started",
                RideResponse.fromEntity(rideService.startRide(rideId, currentDriverId(jwt))));
    }

    @PostMapping("/{rideId}/complete")
    public ApiResponse<RideResponse> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String rideId,
            @RequestBody(required = false) CompleteRideRequest request) {
        return ApiResponse.success("Ride completed",
                RideResponse.fromEntity(rideService.completeRide(rideId, currentDriverId(jwt), request)));
    }

    private String currentDriverId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Driver identity is required");
        }
        return jwt.getSubject();
    }
}

