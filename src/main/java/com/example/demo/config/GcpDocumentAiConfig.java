package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class GcpDocumentAiConfig {

    // application.properties에서 크리덴셜 경로를 주입받습니다.
    @Value("${google.cloud.vision.credentials.path}") 
    private Resource credentialsResource; 

    /**
     * Document AI 클라이언트를 생성하고 Spring Bean으로 등록합니다.
     */
    @Bean
    public DocumentProcessorServiceClient documentProcessorServiceClient() throws IOException {
        
        // 1. application.properties의 경로를 사용하여 GoogleCredentials 로드
        GoogleCredentials credentials = 
            GoogleCredentials.fromStream(credentialsResource.getInputStream());

        // 2. 로드된 인증 정보를 사용하여 클라이언트 설정
        DocumentProcessorServiceSettings settings = 
            DocumentProcessorServiceSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();
        
        // 3. 클라이언트 생성 및 반환
        return DocumentProcessorServiceClient.create(settings);
    }
}