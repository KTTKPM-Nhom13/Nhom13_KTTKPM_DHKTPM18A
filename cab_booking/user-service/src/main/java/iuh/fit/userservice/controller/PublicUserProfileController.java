package iuh.fit.userservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.service.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PublicUserProfileController {
    UserProfileService userProfileService;

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        return ApiResponse.<UserProfileResponse>builder()
                .message("Fetched user profile successfully")
                .result(userProfileService.getProfile(userId))
                .build();
    }
}
