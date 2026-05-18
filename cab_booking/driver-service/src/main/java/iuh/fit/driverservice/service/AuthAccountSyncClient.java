package iuh.fit.driverservice.service;

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
