package com.example.hr_management_backend.core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Value("${app.firebase.credentials-path:firebase-service-account.json}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!firebaseEnabled) {
            log.info("[FirebaseConfig] Firebase Cloud Messaging is disabled by configuration (app.firebase.enabled=false).");
            return null;
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }

            InputStream serviceAccountStream = resolveCredentialsStream();
            if (serviceAccountStream == null) {
                log.warn("[FirebaseConfig] Firebase service account file '{}' not found. Push notifications will be disabled.", credentialsPath);
                return null;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("[FirebaseConfig] FirebaseApp successfully initialized for FCM notifications.");
            return app;
        } catch (Exception e) {
            log.error("[FirebaseConfig] Failed to initialize FirebaseApp: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private InputStream resolveCredentialsStream() {
        try {
            File directFile = new File(credentialsPath);
            if (directFile.exists() && directFile.canRead()) {
                return new FileInputStream(directFile);
            }

            String userDir = System.getProperty("user.dir");
            if (userDir != null) {
                File userDirFile = new File(userDir, credentialsPath);
                if (userDirFile.exists() && userDirFile.canRead()) {
                    return new FileInputStream(userDirFile);
                }
            }

            // Fallback to classpath
            InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(credentialsPath);
            if (resourceStream != null) {
                return resourceStream;
            }
        } catch (Exception e) {
            log.warn("[FirebaseConfig] Could not open credentials file: {}", e.getMessage());
        }
        return null;
    }
}
