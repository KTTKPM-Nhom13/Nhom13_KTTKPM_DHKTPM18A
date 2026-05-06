package iuh.fit.email_service.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.email_service.config.BrevoProperties;
import iuh.fit.email_service.dto.request.SendEmailRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrevoEmailService {
    BrevoProperties brevoProperties;
    RestClient.Builder restClientBuilder = RestClient.builder();

    public void sendEmail(SendEmailRequest request) {
        if (brevoProperties.getApiKey() == null || brevoProperties.getApiKey().isBlank()) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of(
                        "email", brevoProperties.getSenderEmail(),
                        "name", brevoProperties.getSenderName()
                ),
                "to", List.of(Map.of("email", request.getTo())),
                "subject", request.getSubject(),
                "htmlContent", request.getHtmlContent() == null ? "" : request.getHtmlContent(),
                "textContent", request.getTextContent() == null ? "" : request.getTextContent()
        );

        try {
            restClientBuilder.build()
                    .post()
                    .uri(brevoProperties.getBaseUrl() + "/v3/smtp/email")
                    .header("api-key", brevoProperties.getApiKey())
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, ex);
        }
    }
}
