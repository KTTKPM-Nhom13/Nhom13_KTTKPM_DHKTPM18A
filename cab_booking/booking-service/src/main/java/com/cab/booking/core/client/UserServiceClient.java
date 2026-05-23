package com.cab.booking.core.client;

import com.cab.booking.core.dto.response.AdminUserInfoResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class UserServiceClient {
    private final RestClient restClient;

    public UserServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.user.base-url}") String userServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "userFallback")
    @Retry(name = "userService")
    @Bulkhead(name = "userService")
    public AdminUserInfoResponse getUserInfo(String externalUserId, String bearerToken) {
        if (externalUserId == null || externalUserId.isBlank()) {
            return null;
        }
        ApiResponse<List<UserProfilePayload>> response = restClient.get()
                .uri("/api/admin/users")
                .headers(headers -> {
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<UserProfilePayload> users = response == null ? null : response.getResult();
        if (users == null) {
            return null;
        }

        Optional<UserProfilePayload> profile = users.stream()
                .filter(user -> externalUserId.equals(user.getExternalUserId()))
                .findFirst();
        return profile.map(user -> AdminUserInfoResponse.builder()
                .userId(user.getExternalUserId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .build()).orElse(null);
    }

    public AdminUserInfoResponse userFallback(String externalUserId, String bearerToken, Throwable ex) {
        log.warn("Could not enrich user info from user-service | userId={} | reason={}",
                externalUserId, ex.getMessage());
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ApiResponse<T> {
        private T result;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UserProfilePayload {
        private String externalUserId;
        private String fullName;
        private String phoneNumber;
    }
}
