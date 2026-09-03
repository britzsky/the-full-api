package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class GcpDocumentAiConfig {

    // application.properties에서 크리덴셜 경로를 주입받습니다.
    @Value("${google.cloud.vision.credentials.path:}")
    private String credentialsPath;

    private final ResourceLoader resourceLoader;

    public GcpDocumentAiConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Document AI 클라이언트를 생성하고 Spring Bean으로 등록합니다.
     */
    @Bean
    public DocumentProcessorServiceClient documentProcessorServiceClient() throws IOException {
        
        GoogleCredentials credentials = loadCredentials();

        // 2. 로드된 인증 정보를 사용하여 클라이언트 설정
        DocumentProcessorServiceSettings settings = 
            DocumentProcessorServiceSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
        
        // 3. 클라이언트 생성 및 반환
        return DocumentProcessorServiceClient.create(settings);
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            try {
                return GoogleCredentials.getApplicationDefault();
            } catch (IOException e) {
                throw new IOException(
                        "Google Document AI credentials are not configured. "
                                + "Set GOOGLE_APPLICATION_CREDENTIALS or "
                                + "google.cloud.vision.credentials.path.",
                        e);
            }
        }

        String location = credentialsPath.trim();
        if (!location.contains(":")) {
            location = "file:" + location;
        }
        Resource credentialsResource = resourceLoader.getResource(location);
        if (!credentialsResource.exists() || !credentialsResource.isReadable()) {
            throw new IOException("Google credentials file is not readable: " + location);
        }
        try (InputStream inputStream = credentialsResource.getInputStream()) {
            return GoogleCredentials.fromStream(inputStream);
        }
    }
}
