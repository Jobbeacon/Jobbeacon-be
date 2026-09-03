package com.jobbeacon.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "auth.provider", havingValue = "firebase")
public class FirebaseConfiguration {

  @Value("${firebase.credentials-path:firebase-service-account.json}")
  private String credentialsPath;

  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    log.info("(credentialsPath) :{}", this.credentialsPath);

    if (!FirebaseApp.getApps().isEmpty()) {
      log.info("FirebaseApp already initialized");

      return FirebaseApp.getInstance();
    }

    final Resource resource = resolveCredentialsResource();
    try (InputStream serviceAccount = resource.getInputStream()) {
      final FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
          .build();

      final FirebaseApp app = FirebaseApp.initializeApp(options);
      log.info("Firebase initialized successfully from: {}", credentialsPath);

      return app;
    }
  }

  @Bean
  public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
    return FirebaseAuth.getInstance(firebaseApp);
  }

  /**
   * Resolve credentials file from classpath or filesystem.
   */
  private Resource resolveCredentialsResource() {
    // Try classpath first
    final Resource classpathResource = new ClassPathResource(credentialsPath);
    if (classpathResource.exists()) {
      log.info("Loading Firebase credentials from classpath: {}", credentialsPath);

      return classpathResource;
    }

    // Fallback to filesystem
    final Resource fileResource = new FileSystemResource(credentialsPath);
    if (fileResource.exists()) {
      log.info("Loading Firebase credentials from filesystem: {}", credentialsPath);

      return fileResource;
    }

    throw new IllegalStateException(
        "Firebase credentials file not found: " + credentialsPath
            + ". Place it in classpath or provide absolute path via firebase.credentials-path");
  }
}
