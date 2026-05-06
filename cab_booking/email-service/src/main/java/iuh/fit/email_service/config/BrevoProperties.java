package iuh.fit.email_service.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "integration.brevo")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BrevoProperties {
    String baseUrl;
    String apiKey;
    String senderEmail;
    String senderName;
}
