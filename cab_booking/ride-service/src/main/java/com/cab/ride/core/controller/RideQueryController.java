package com.cab.ride.core.controller;

import com.cab.ride.core.dto.response.RideResponse;
import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.service.RideService;
import iuh.fit.common.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only controller for ride queries.
 * Separated from {@link RideLifecycleController} because lifecycle endpoints
 * require DRIVER role at class level, while this controller allows
 * CUSTOMER (owner), DRIVER (assigned), and ADMIN.
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideQueryController {

    private final RideService rideService;

    @GetMapping("/{rideId}")
    public ApiResponse<RideResponse> getRideById(
            @PathVariable String rideId,
            @AuthenticationPrincipal Jwt jwt) {

        Ride ride = rideService.getRideById(rideId);
        authorizeRideAccess(jwt, ride);
        return ApiResponse.success("Fetched ride successfully", RideResponse.fromEntity(ride));
    }

    private void authorizeRideAccess(Jwt jwt, Ride ride) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String userId = jwt.getSubject();

        // ADMIN can access any ride
        boolean isAdmin = jwt.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        // Customer owner
        if (userId.equals(ride.getCustomerId())) {
            return;
        }

        // Assigned driver
        if (userId.equals(ride.getDriverId())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this ride");
    }
}
