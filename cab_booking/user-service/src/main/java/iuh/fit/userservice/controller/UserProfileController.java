package iuh.fit.userservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.userservice.dto.request.UpsertUserProfileRequest;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.security.CurrentUserFacade;
import iuh.fit.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/profile")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {
    UserProfileService userProfileService;
    CurrentUserFacade currentUserFacade;

    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile() {
        return ApiResponse.<UserProfileResponse>builder()
                .message("Fetched user profile successfully")
                .result(userProfileService.getProfile(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> upsertMyProfile(@Valid @RequestBody UpsertUserProfileRequest request) {
        return ApiResponse.<UserProfileResponse>builder()
                .message("Saved user profile successfully")
                .result(userProfileService.upsertProfile(currentUserFacade.getCurrentUserId(), request))
                .build();
    }
}
