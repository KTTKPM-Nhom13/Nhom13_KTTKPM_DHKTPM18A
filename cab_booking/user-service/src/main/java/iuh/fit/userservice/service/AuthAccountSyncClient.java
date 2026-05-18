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

    public String registerAuthAccount(java.util.Map<String, Object> payload) {
        try {
            var response = restClientBuilder.build()
                    .post()
                    .uri(authServiceUrl + "/auth/register")
                    .body(payload)
                    .retrieve()
                    .body(java.util.Map.class);
            
            if (response != null && response.containsKey("result")) {
                java.util.Map<String, Object> result = (java.util.Map<String, Object>) response.get("result");
                if (result != null && result.containsKey("user")) {
                    java.util.Map<String, Object> user = (java.util.Map<String, Object>) result.get("user");
                    return (String) user.get("userId");
                }
            }
            throw new RuntimeException("Could not extract userId from auth-service response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to register auth account: " + e.getMessage(), e);
        }
    }
}
