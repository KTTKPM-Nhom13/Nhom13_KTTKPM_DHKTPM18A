package iuh.fit.userservice.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.userservice.dto.request.RequestAccountDeletionRequest;
import iuh.fit.userservice.dto.response.UserAccountResponse;
import iuh.fit.userservice.security.CurrentUserFacade;
import iuh.fit.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/account")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountController {
    UserProfileService userProfileService;
    CurrentUserFacade currentUserFacade;

    @GetMapping
    public ApiResponse<UserAccountResponse> getMyAccount() {
        return ApiResponse.<UserAccountResponse>builder()
                .message("Fetched user account successfully")
                .result(userProfileService.getAccount(currentUserFacade.getCurrentUserId()))
                .build();
    }

    @PostMapping("/delete-request")
    public ApiResponse<UserAccountResponse> requestDeletion(
            @Valid @RequestBody RequestAccountDeletionRequest request) {
        return ApiResponse.<UserAccountResponse>builder()
                .message("Requested account deletion successfully")
                .result(userProfileService.requestAccountDeletion(currentUserFacade.getCurrentUserId(), request))
                .build();
    }

    @PostMapping("/restore")
    public ApiResponse<UserAccountResponse> restoreAccount() {
        return ApiResponse.<UserAccountResponse>builder()
                .message("Restored user account successfully")
                .result(userProfileService.restoreAccount(currentUserFacade.getCurrentUserId()))
                .build();
    }
}
