package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.SyncAccountLifecycleRequest;
import iuh.fit.userservice.entity.UserProfile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthAccountSyncClient {
    final RestClient.Builder restClientBuilder = RestClient.builder();

    @Value("${integration.auth-service.url:http://auth-service:8081}")
    String authServiceUrl;

    public void syncAccountLifecycle(UserProfile profile) {
        restClientBuilder.build()
                .post()
                .uri(authServiceUrl + "/internal/auth/users/{userId}/account-lifecycle", profile.getExternalUserId())
                .body(SyncAccountLifecycleRequest.builder()
                        .accountStatus(profile.getAccountStatus().name())
                        .deletionRequestedAt(profile.getDeletionRequestedAt())
                        .scheduledDeletionAt(profile.getScheduledDeletionAt())
                        .deletionReason(profile.getDeletionReason())
                        .build())
                .retrieve()
                .toBodilessEntity();
    }
}
