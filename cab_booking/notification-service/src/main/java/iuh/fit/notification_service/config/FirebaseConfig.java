package iuh.fit.notification_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {
        try {
            ClassPathResource resource = new ClassPathResource("serviceAccountKey.json");
            if (!resource.exists()) {
                log.warn("⚠️ Firebase serviceAccountKey.json not found in resources! FCM Push Notification will be disabled.");
                return;
            }

            try (InputStream serviceAccount = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    log.info("🚀 Google Firebase Admin SDK initialized successfully!");
                } else {
                    log.info("Google Firebase Admin SDK is already initialized.");
                }
            }
        } catch (IOException e) {
            log.error("❌ Failed to parse Firebase serviceAccountKey.json credentials: {}. FCM Push Notification will be disabled.", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error during Firebase Admin SDK initialization: {}. FCM Push Notification will be disabled.", e.getMessage());
        }
    }
}
