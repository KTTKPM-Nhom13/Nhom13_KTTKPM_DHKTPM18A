package iuh.fit.driverservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.driverservice.dto.request.CompleteDriverRideRequest;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverRideProgressRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverCurrentRideResponse;
import iuh.fit.driverservice.dto.response.DriverEarningsSummaryResponse;
import iuh.fit.driverservice.dto.response.DriverProfileResponse;
import iuh.fit.driverservice.security.CurrentUserFacade;
import iuh.fit.driverservice.service.DriverProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DriverProfileController {
    DriverProfileService driverProfileService;
    CurrentUserFacade currentUserFacade;

    @GetMapping("/profile")
    public ApiResponse<DriverProfileResponse> getProfile() {
        return ApiResponse.<DriverProfileResponse>builder()
                .message("Fetched driver profile successfully")
                .result(driverProfileService.getProfile(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PutMapping("/profile")
    public ApiResponse<DriverProfileResponse> upsertProfile(@Valid @RequestBody UpsertDriverProfileRequest request) {
        return ApiResponse.<DriverProfileResponse>builder()
                .message("Saved driver profile successfully")
                .result(driverProfileService.upsertProfile(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PatchMapping("/availability")
    public ApiResponse<DriverAvailabilityResponse> updateAvailability(
            @Valid @RequestBody UpdateDriverAvailabilityRequest request) {
        return ApiResponse.<DriverAvailabilityResponse>builder()
                .message("Updated driver availability successfully")
                .result(driverProfileService.updateAvailability(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @GetMapping("/current-ride")
    public ApiResponse<DriverCurrentRideResponse> getCurrentRide() {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Fetched current ride successfully")
                .result(driverProfileService.getCurrentRide(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PostMapping("/rides/assignment")
    public ApiResponse<DriverCurrentRideResponse> handleAssignment(
            @Valid @RequestBody HandleDriverAssignmentRequest request) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Handled driver assignment successfully")
                .result(driverProfileService.handleAssignment(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PostMapping("/rides/{rideId}/accept")
    public ApiResponse<DriverCurrentRideResponse> acceptRide(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride accept request submitted")
                .result(driverProfileService.acceptRide(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @PostMapping("/rides/{rideId}/reject")
    public ApiResponse<DriverCurrentRideResponse> rejectRide(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride reject request submitted")
                .result(driverProfileService.rejectRide(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @PostMapping("/rides/{rideId}/arrive")
    public ApiResponse<DriverCurrentRideResponse> arriveAtPickup(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Driver arrival submitted")
                .result(driverProfileService.arriveAtPickup(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @PostMapping("/rides/{rideId}/start")
    public ApiResponse<DriverCurrentRideResponse> startRide(@PathVariable String rideId) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride start submitted")
                .result(driverProfileService.startRide(currentUserFacade.getCurrentUserId(), rideId))
                .build();
    }

    @PostMapping("/rides/{rideId}/complete")
    public ApiResponse<DriverCurrentRideResponse> completeRide(
            @PathVariable String rideId,
            @Valid @RequestBody CompleteDriverRideRequest request) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Ride completion submitted")
                .result(driverProfileService.completeRide(currentUserFacade.getCurrentUserId(), rideId, request))
                .build();
    }

    @PatchMapping("/rides/current")
    public ApiResponse<DriverCurrentRideResponse> updateRideProgress(
            @Valid @RequestBody UpdateDriverRideProgressRequest request) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Updated current ride successfully")
                .result(driverProfileService.updateRideProgress(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PostMapping("/rides/current/complete")
    public ApiResponse<DriverCurrentRideResponse> completeRide(
            @Valid @RequestBody CompleteDriverRideRequest request) {
        return ApiResponse.<DriverCurrentRideResponse>builder()
                .message("Completed current ride successfully")
                .result(driverProfileService.completeCurrentRide(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @GetMapping("/earnings/summary")
    public ApiResponse<DriverEarningsSummaryResponse> getEarningsSummary() {
        return ApiResponse.<DriverEarningsSummaryResponse>builder()
                .message("Fetched driver earnings summary successfully")
                .result(driverProfileService.getEarningsSummary(currentUserFacade.getCurrentUserId()))
                .build();
    }
}
