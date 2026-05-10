package iuh.fit.auth_service.service;

import iuh.fit.auth_service.dto.response.EmailDispatchRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailServiceClient {
    final RestClient.Builder restClientBuilder = RestClient.builder();

    @Value("${integration.email-service.url:http://email-service:8087}")
    String emailServiceUrl;

    public void sendWelcomeEmail(String to, String fullName) {
        EmailDispatchRequest request = EmailDispatchRequest.builder()
                .to(to)
                .subject("Welcome to CAB Booking")
                .textContent("Hello " + fullName + ", welcome to CAB Booking.")
                .htmlContent("<p>Hello <strong>" + fullName + "</strong>, welcome to CAB Booking.</p>")
                .build();

        try {
            restClientBuilder.build()
                    .post()
                    .uri(emailServiceUrl + "/internal/emails/send")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to send welcome email to {}", to, ex);
        }
    }

    public void sendRegistrationOtpEmail(String to, String otpCode, long otpMinutes) {
        EmailDispatchRequest request = EmailDispatchRequest.builder()
                .to(to)
                .subject("Your CAB Booking verification code")
                .textContent("Your verification code is " + otpCode + ". It expires in " + otpMinutes + " minutes.")
                .htmlContent("<p>Your verification code is <strong>" + otpCode
                        + "</strong>.</p><p>It expires in " + otpMinutes + " minutes.</p>")
                .build();

        try {
            restClientBuilder.build()
                    .post()
                    .uri(emailServiceUrl + "/internal/emails/send")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to send registration OTP email to {}", to, ex);
            throw ex;
        }
    }

    public void sendForgotPasswordOtpEmail(String to, String otpCode, long otpMinutes) {
        EmailDispatchRequest request = EmailDispatchRequest.builder()
                .to(to)
                .subject("Your CAB Booking password reset code")
                .textContent("Your password reset code is " + otpCode + ". It expires in " + otpMinutes + " minutes.")
                .htmlContent("<p>Your password reset code is <strong>" + otpCode
                        + "</strong>.</p><p>It expires in " + otpMinutes + " minutes.</p>")
                .build();

        try {
            restClientBuilder.build()
                    .post()
                    .uri(emailServiceUrl + "/internal/emails/send")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to send forgot-password OTP email to {}", to, ex);
            throw ex;
        }
    }
}
